package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.dto.request.NationwideSearchRequest;
import com.example.karrotsearch.domain.search.dto.response.NationwideSearchResponse;
import com.example.karrotsearch.domain.search.dto.response.RegionResponse;
import com.example.karrotsearch.domain.search.entity.Region;
import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.repository.ListingSearchRepository;
import com.example.karrotsearch.domain.search.repository.RegionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NationwideSearchService {

  private static final String DEFAULT_REGION_ID = "seoul-gangnam";
  private static final int DEFAULT_MAX_STOPS = 24;
  private static final int TARGET_MAX_STOPS = 12;
  private static final List<Integer> AUTO_RADIUS_CANDIDATES =
      List.of(8, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100, 120, 150, 200);

  private final RegionRepository regionRepository;
  private final ListingSearchRepository listingSearchRepository;

  public List<RegionResponse> findRegions() {
    return regionRepository.findAllByOrderByProvinceAscNameAsc().stream()
        .map(RegionResponse::from)
        .toList();
  }

  public NationwideSearchResponse search(NationwideSearchRequest request) {
    long totalStartedAt = System.nanoTime();
    long planningStartedAt = System.nanoTime();
    SearchPlan plan =
        request.getRadiusKm() == null
            ? buildOptimizedPlan(request.getStartRegionId(), request.getMaxStops())
            : buildPlan(
                request.getStartRegionId(),
                request.getRadiusKm(),
                request.getMaxStops() == null ? DEFAULT_MAX_STOPS : request.getMaxStops(),
                "MANUAL_RADIUS");
    long planningTimeMs = elapsedMs(planningStartedAt);

    long listingFetchStartedAt = System.nanoTime();
    List<SearchListing> listings = listingSearchRepository.search(request.getKeyword(), plan);
    long listingFetchTimeMs = elapsedMs(listingFetchStartedAt);

    SearchExecutionMetrics metrics =
        SearchExecutionMetrics.builder()
            .planningTimeMs(planningTimeMs)
            .listingFetchTimeMs(listingFetchTimeMs)
            .totalElapsedTimeMs(elapsedMs(totalStartedAt))
            .listingProvider(listingSearchRepository.providerName())
            .build();

    return NationwideSearchResponse.of(plan, listings, metrics);
  }

  private SearchPlan buildOptimizedPlan(String startRegionId, Integer maxStops) {
    int stopLimit = maxStops == null ? DEFAULT_MAX_STOPS : maxStops;

    SearchPlan fallback = null;
    for (int radiusKm : AUTO_RADIUS_CANDIDATES) {
      SearchPlan plan = buildPlan(startRegionId, radiusKm, stopLimit, "AUTO_OPTIMIZED_RADIUS");
      if (fallback == null || isBetter(plan, fallback)) {
        fallback = plan;
      }
      if (plan.getRemainingRegions().isEmpty() && plan.getSteps().size() <= TARGET_MAX_STOPS) {
        return plan;
      }
    }
    return fallback;
  }

  private boolean isBetter(SearchPlan candidate, SearchPlan currentBest) {
    if (candidate.getRemainingRegions().size() != currentBest.getRemainingRegions().size()) {
      return candidate.getRemainingRegions().size() < currentBest.getRemainingRegions().size();
    }
    if (candidate.getSteps().size() != currentBest.getSteps().size()) {
      return candidate.getSteps().size() < currentBest.getSteps().size();
    }
    return candidate.getTotalMoveKm() < currentBest.getTotalMoveKm();
  }

  private SearchPlan buildPlan(String startRegionId, int radiusKm, int maxStops, String strategy) {
    List<Region> regions = regionRepository.findAllByOrderByProvinceAscNameAsc();
    RegionDistanceMatrix distanceMatrix = new RegionDistanceMatrix(regions);
    Set<Region> covered = new LinkedHashSet<>();
    Set<Region> selected = new LinkedHashSet<>();
    List<CoverageStep> steps = new ArrayList<>();
    double totalMoveKm = 0;
    Region previous = null;

    for (int sequence = 1; sequence <= maxStops; sequence++) {
      Region current =
          selectNextRegion(startRegionId, regions, covered, selected, previous, radiusKm, distanceMatrix);
      if (current == null) {
        break;
      }
      selected.add(current);

      double movedKm = previous == null ? 0 : distanceMatrix.distance(previous, current);
      totalMoveKm += movedKm;

      List<Region> newlyCovered =
          regions.stream()
              .filter(region -> distanceMatrix.distance(current, region) <= radiusKm)
              .filter(region -> !covered.contains(region))
              .toList();
      covered.addAll(newlyCovered);

      steps.add(
          CoverageStep.builder()
              .sequence(sequence)
              .region(current)
              .distanceFromPreviousKm(round(movedKm))
              .newlyCoveredRegions(newlyCovered.size())
              .totalCoveredRegions(covered.size())
              .coveragePercent(percentage(covered.size(), regions.size()))
              .build());

      if (covered.size() == regions.size()) {
        break;
      }

      previous = current;
    }

    List<Region> remaining = regions.stream().filter(region -> !covered.contains(region)).toList();
    return SearchPlan.builder()
        .radiusKm(radiusKm)
        .strategy(strategy)
        .steps(List.copyOf(steps))
        .coveredRegions(List.copyOf(covered))
        .remainingRegions(remaining)
        .coveragePercent(percentage(covered.size(), regions.size()))
        .totalMoveKm(round(totalMoveKm))
        .build();
  }

  private Region selectNextRegion(
      String startRegionId,
      List<Region> regions,
      Set<Region> covered,
      Set<Region> selected,
      Region previous,
      int radiusKm,
      RegionDistanceMatrix distanceMatrix) {
    if (previous == null && startRegionId != null && !startRegionId.isBlank()) {
      return findStartRegion(startRegionId);
    }

    return regions.stream()
        .filter(region -> !selected.contains(region))
        .max(
            Comparator.comparingInt(
                    (Region region) ->
                        countNewlyCovered(region, regions, covered, radiusKm, distanceMatrix))
                .thenComparingDouble(region -> distanceScore(previous, region, distanceMatrix)))
        .filter(region -> countNewlyCovered(region, regions, covered, radiusKm, distanceMatrix) > 0)
        .orElseGet(() -> findFarthestUncoveredRegion(previous, regions, covered, distanceMatrix));
  }

  private int countNewlyCovered(
      Region candidate,
      List<Region> regions,
      Set<Region> covered,
      int radiusKm,
      RegionDistanceMatrix distanceMatrix) {
    return (int)
        regions.stream()
            .filter(region -> !covered.contains(region))
            .filter(region -> distanceMatrix.distance(candidate, region) <= radiusKm)
            .count();
  }

  private double distanceScore(Region previous, Region candidate, RegionDistanceMatrix distanceMatrix) {
    if (previous == null) {
      return -distanceFromNationalCenter(candidate, distanceMatrix);
    }
    return -distanceMatrix.distance(previous, candidate);
  }

  private double distanceFromNationalCenter(Region candidate, RegionDistanceMatrix distanceMatrix) {
    Region center = findStartRegion(DEFAULT_REGION_ID);
    return distanceMatrix.distance(center, candidate);
  }

  private Region findStartRegion(String startRegionId) {
    return regionRepository
        .findById(startRegionId)
        .or(() -> regionRepository.findById(DEFAULT_REGION_ID))
        .orElseThrow(() -> new IllegalStateException("기본 시작 지역이 없습니다."));
  }

  private Region findFarthestUncoveredRegion(
      Region current, List<Region> regions, Set<Region> covered, RegionDistanceMatrix distanceMatrix) {
    if (current == null) {
      return regions.stream().filter(region -> !covered.contains(region)).findFirst().orElse(null);
    }
    return regions.stream()
        .filter(region -> !covered.contains(region))
        .max(Comparator.comparingDouble(region -> distanceMatrix.distance(current, region)))
        .orElse(current);
  }

  private double percentage(int part, int total) {
    if (total == 0) {
      return 0;
    }
    return round(part * 100.0 / total);
  }

  private double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}
