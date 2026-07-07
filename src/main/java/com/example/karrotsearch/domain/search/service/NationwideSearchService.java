package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.dto.request.NationwideSearchRequest;
import com.example.karrotsearch.domain.search.dto.response.NationwideSearchResponse;
import com.example.karrotsearch.domain.search.dto.response.RegionCoverageResponse;
import com.example.karrotsearch.domain.search.dto.response.RegionResponse;
import com.example.karrotsearch.domain.search.entity.Region;
import com.example.karrotsearch.domain.search.entity.RegionCoverage;
import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.repository.ListingSearchRepository;
import com.example.karrotsearch.domain.search.repository.RegionCoverageRepository;
import com.example.karrotsearch.domain.search.repository.RegionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private final RegionCoverageRepository regionCoverageRepository;
  private final ListingSearchRepository listingSearchRepository;

  public List<RegionResponse> findRegions() {
    return regionRepository.findAllByOrderByProvinceAscNameAsc().stream()
        .map(RegionResponse::from)
        .toList();
  }

  public List<RegionCoverageResponse> findObservedCoverages() {
    return regionCoverageRepository.findAll().stream().map(RegionCoverageResponse::from).toList();
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
    Map<String, Set<String>> observedCoverageByProviderRegionId =
        distanceCoverage ? Map.of() : observedCoverageByProviderRegionId(regions);
    Set<Region> covered = new LinkedHashSet<>();
    Set<Region> selected = new LinkedHashSet<>();
    List<CoverageStep> steps = new ArrayList<>();
    double totalMoveKm = 0;
    Region previous = null;

    for (int sequence = 1; sequence <= maxStops; sequence++) {
      Region current =
          selectNextRegion(
              startRegionId,
              regions,
              covered,
              selected,
              previous,
              radiusKm,
              distanceMatrix,
              distanceCoverage,
              observedCoverageByProviderRegionId);
      if (current == null) {
        break;
      }
      selected.add(current);

      double movedKm = previous == null ? 0 : distanceMatrix.distance(previous, current);
      totalMoveKm += movedKm;

      List<Region> newlyCovered =
          findNewlyCoveredRegions(
              current,
              regions,
              covered,
              radiusKm,
              distanceMatrix,
              distanceCoverage,
              observedCoverageByProviderRegionId);
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
      boolean distanceCoverage,
      Map<String, Set<String>> observedCoverageByProviderRegionId) {
    if (previous == null) {
      return startRegionId == null || startRegionId.isBlank()
          ? findStartRegion(DEFAULT_REGION_ID)
          : findStartRegion(startRegionId);
    }
    if (!distanceCoverage) {
      return selectNextObservedCoverageRegion(
          regions, covered, selected, distanceMatrix, observedCoverageByProviderRegionId);
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

  private Region selectNextSpreadRegion(
      List<Region> regions, Set<Region> selected, RegionDistanceMatrix distanceMatrix) {
    return regions.stream()
        .filter(region -> !selected.contains(region))
        .max(Comparator.comparingDouble(region -> nearestSelectedDistance(region, selected, distanceMatrix)))
        .orElse(null);
  }

  private Region selectNextObservedCoverageRegion(
      List<Region> regions,
      Set<Region> covered,
      Set<Region> selected,
      RegionDistanceMatrix distanceMatrix,
      Map<String, Set<String>> observedCoverageByProviderRegionId) {
    Set<String> coveredProviderRegionIds = providerRegionIds(covered);
    return regions.stream()
        .filter(region -> !selected.contains(region))
        .max(
            Comparator.comparingInt(
                    (Region region) ->
                        countObservedNewCoverage(
                            region, coveredProviderRegionIds, observedCoverageByProviderRegionId))
                .thenComparingDouble(region -> nearestSelectedDistance(region, selected, distanceMatrix)))
        .orElse(null);
  }

  private double nearestSelectedDistance(
      Region candidate, Set<Region> selected, RegionDistanceMatrix distanceMatrix) {
    if (selected.isEmpty()) {
      return 0;
    }
    return selected.stream()
        .mapToDouble(selectedRegion -> distanceMatrix.distance(candidate, selectedRegion))
        .min()
        .orElse(0);
  }

  private List<Region> findNewlyCoveredRegions(
      Region current,
      List<Region> regions,
      Set<Region> covered,
      int radiusKm,
      RegionDistanceMatrix distanceMatrix,
      boolean distanceCoverage,
      Map<String, Set<String>> observedCoverageByProviderRegionId) {
    if (!distanceCoverage) {
      Set<String> observedProviderRegionIds =
          observedCoverageByProviderRegionId.getOrDefault(
              current.getProviderRegionId(), Set.of(current.getProviderRegionId()));
      return regions.stream()
          .filter(region -> !covered.contains(region))
          .filter(region -> observedProviderRegionIds.contains(region.getProviderRegionId()))
          .toList();
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

  private Map<String, Set<String>> observedCoverageByProviderRegionId(List<Region> regions) {
    List<String> providerRegionIds =
        regions.stream().map(Region::getProviderRegionId).filter(this::hasText).toList();
    Map<String, Set<String>> coverage = new HashMap<>();
    for (String providerRegionId : providerRegionIds) {
      coverage.put(providerRegionId, new LinkedHashSet<>(List.of(providerRegionId)));
    }
    for (RegionCoverage regionCoverage :
        regionCoverageRepository.findBySourceProviderRegionIdIn(providerRegionIds)) {
      coverage
          .computeIfAbsent(regionCoverage.getSourceProviderRegionId(), ignored -> new LinkedHashSet<>())
          .add(regionCoverage.getCoveredProviderRegionId());
    }
    return coverage;
  }

  private int countObservedNewCoverage(
      Region candidate,
      Set<String> coveredProviderRegionIds,
      Map<String, Set<String>> observedCoverageByProviderRegionId) {
    Set<String> observedProviderRegionIds =
        observedCoverageByProviderRegionId.getOrDefault(
            candidate.getProviderRegionId(), Set.of(candidate.getProviderRegionId()));
    return (int)
        observedProviderRegionIds.stream()
            .filter(providerRegionId -> !coveredProviderRegionIds.contains(providerRegionId))
            .count();
  }

  private Set<String> providerRegionIds(Set<Region> regions) {
    Set<String> providerRegionIds = new LinkedHashSet<>();
    for (Region region : regions) {
      if (hasText(region.getProviderRegionId())) {
        providerRegionIds.add(region.getProviderRegionId());
      }
    }
    return providerRegionIds;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
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
