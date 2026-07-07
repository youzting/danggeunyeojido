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
  private static final int HUB_SPACING_KM = 50;

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
            ? buildFiftyKmHubPlan(request.getStartRegionId(), request.getMaxStops())
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

  private SearchPlan buildFiftyKmHubPlan(String startRegionId, Integer maxStops) {
    int stopLimit = maxStops == null ? DEFAULT_MAX_STOPS : maxStops;
    String strategy =
        listingSearchRepository.supportsDistanceCoverage()
            ? "FIFTY_KM_HUB_GRID"
            : "FIFTY_KM_PROVIDER_REGION_HUBS";
    return buildPlan(startRegionId, HUB_SPACING_KM, stopLimit, strategy);
  }

  private SearchPlan buildPlan(String startRegionId, int radiusKm, int maxStops, String strategy) {
    List<Region> regions = regionRepository.findAllByOrderByProvinceAscNameAsc();
    RegionDistanceMatrix distanceMatrix = new RegionDistanceMatrix(regions);
    boolean distanceCoverage = listingSearchRepository.supportsDistanceCoverage();
    Set<Region> covered = new LinkedHashSet<>();
    Set<Region> selected = new LinkedHashSet<>();
    List<CoverageStep> steps = new ArrayList<>();
    double totalMoveKm = 0;
    Region previous = null;

    for (int sequence = 1; sequence <= maxStops; sequence++) {
      Region current =
          selectNextRegion(
              startRegionId, regions, covered, selected, previous, radiusKm, distanceMatrix, distanceCoverage);
      if (current == null) {
        break;
      }
      selected.add(current);

      double movedKm = previous == null ? 0 : distanceMatrix.distance(previous, current);
      totalMoveKm += movedKm;

      List<Region> newlyCovered =
          findNewlyCoveredRegions(current, regions, covered, radiusKm, distanceMatrix, distanceCoverage);
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
      RegionDistanceMatrix distanceMatrix,
      boolean distanceCoverage) {
    if (previous == null && startRegionId != null && !startRegionId.isBlank()) {
      return findStartRegion(startRegionId);
    }

    return regions.stream()
        .filter(region -> !selected.contains(region))
        .max(
            Comparator.comparingInt(
                    (Region region) ->
                        countNewlyCovered(
                            region, regions, covered, radiusKm, distanceMatrix, distanceCoverage))
                .thenComparingDouble(region -> distanceScore(previous, region, distanceMatrix)))
        .filter(
            region ->
                countNewlyCovered(
                        region, regions, covered, radiusKm, distanceMatrix, distanceCoverage)
                    > 0)
        .orElseGet(() -> findFarthestUncoveredRegion(previous, regions, covered, distanceMatrix));
  }

  private List<Region> findNewlyCoveredRegions(
      Region current,
      List<Region> regions,
      Set<Region> covered,
      int radiusKm,
      RegionDistanceMatrix distanceMatrix,
      boolean distanceCoverage) {
    if (!distanceCoverage) {
      return covered.contains(current) ? List.of() : List.of(current);
    }
    return regions.stream()
        .filter(region -> distanceMatrix.distance(current, region) <= radiusKm)
        .filter(region -> !covered.contains(region))
        .toList();
  }

  private int countNewlyCovered(
      Region candidate,
      List<Region> regions,
      Set<Region> covered,
      int radiusKm,
      RegionDistanceMatrix distanceMatrix,
      boolean distanceCoverage) {
    if (!distanceCoverage) {
      return covered.contains(candidate) ? 0 : 1;
    }
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
