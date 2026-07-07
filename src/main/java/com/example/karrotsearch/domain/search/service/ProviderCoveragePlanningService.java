package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.dto.response.ProviderCoverageHubResponse;
import com.example.karrotsearch.domain.search.dto.response.ProviderCoveragePlanResponse;
import com.example.karrotsearch.domain.search.dto.response.ProviderRegionResponse;
import com.example.karrotsearch.domain.search.entity.ProviderRegion;
import com.example.karrotsearch.domain.search.entity.RegionCoverage;
import com.example.karrotsearch.domain.search.repository.ProviderRegionRepository;
import com.example.karrotsearch.domain.search.repository.RegionCoverageRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class ProviderCoveragePlanningService {

  private static final int DEFAULT_MAX_HUBS = 50;

  private final ProviderRegionRepository providerRegionRepository;
  private final RegionCoverageRepository regionCoverageRepository;

  public List<ProviderRegionResponse> findProviderRegions() {
    return providerRegionRepository.findAllByOrderByProviderRegionNameAsc().stream()
        .map(ProviderRegionResponse::from)
        .toList();
  }

  public ProviderCoveragePlanResponse buildCoveragePlan(Integer maxHubs) {
    int hubLimit = maxHubs == null ? DEFAULT_MAX_HUBS : maxHubs;
    Map<String, ProviderRegion> regionsById = providerRegionsById();
    Map<String, Set<String>> coverageBySourceId = coverageBySourceId(regionsById.keySet());
    Set<String> coveredIds = new LinkedHashSet<>();
    Set<String> selectedIds = new LinkedHashSet<>();
    List<ProviderCoverageHubResponse> hubs = new ArrayList<>();

    for (int sequence = 1; sequence <= hubLimit; sequence++) {
      String nextHubId = selectNextHub(regionsById.keySet(), selectedIds, coveredIds, coverageBySourceId);
      if (nextHubId == null) {
        break;
      }
      selectedIds.add(nextHubId);

      Set<String> coverableIds =
          coverageBySourceId.getOrDefault(nextHubId, new LinkedHashSet<>(List.of(nextHubId)));
      List<ProviderRegionResponse> newlyCoveredRegions =
          coverableIds.stream()
              .filter(regionsById::containsKey)
              .filter(regionId -> !coveredIds.contains(regionId))
              .map(regionsById::get)
              .sorted(Comparator.comparing(ProviderRegion::getProviderRegionName))
              .map(ProviderRegionResponse::from)
              .toList();
      coveredIds.addAll(newlyCoveredRegions.stream().map(ProviderRegionResponse::getProviderRegionId).toList());

      ProviderRegion hub = regionsById.get(nextHubId);
      hubs.add(
          ProviderCoverageHubResponse.builder()
              .sequence(sequence)
              .providerRegionId(hub.getProviderRegionId())
              .providerRegionName(hub.getProviderRegionName())
              .coveredRegionCount(coverableIds.size())
              .newlyCoveredRegionCount(newlyCoveredRegions.size())
              .newlyCoveredRegions(newlyCoveredRegions)
              .build());

      if (coveredIds.size() == regionsById.size()) {
        break;
      }
    }

    List<ProviderRegionResponse> remainingRegions =
        regionsById.values().stream()
            .filter(region -> !coveredIds.contains(region.getProviderRegionId()))
            .sorted(Comparator.comparing(ProviderRegion::getProviderRegionName))
            .map(ProviderRegionResponse::from)
            .toList();

    return ProviderCoveragePlanResponse.builder()
        .totalProviderRegionCount(regionsById.size())
        .selectedHubCount(hubs.size())
        .coveragePercent(percentage(coveredIds.size(), regionsById.size()))
        .hubs(List.copyOf(hubs))
        .remainingRegions(remainingRegions)
        .build();
  }

  private Map<String, ProviderRegion> providerRegionsById() {
    Map<String, ProviderRegion> regionsById = new LinkedHashMap<>();
    for (ProviderRegion providerRegion : providerRegionRepository.findAllByOrderByProviderRegionNameAsc()) {
      regionsById.put(providerRegion.getProviderRegionId(), providerRegion);
    }
    return regionsById;
  }

  private Map<String, Set<String>> coverageBySourceId(Set<String> providerRegionIds) {
    Map<String, Set<String>> coverageBySourceId = new HashMap<>();
    for (String providerRegionId : providerRegionIds) {
      coverageBySourceId.put(providerRegionId, new LinkedHashSet<>(List.of(providerRegionId)));
    }
    for (RegionCoverage coverage :
        regionCoverageRepository.findBySourceProviderRegionIdIn(providerRegionIds)) {
      if (!providerRegionIds.contains(coverage.getCoveredProviderRegionId())) {
        continue;
      }
      coverageBySourceId
          .computeIfAbsent(coverage.getSourceProviderRegionId(), ignored -> new LinkedHashSet<>())
          .add(coverage.getCoveredProviderRegionId());
    }
    return coverageBySourceId;
  }

  private String selectNextHub(
      Set<String> providerRegionIds,
      Set<String> selectedIds,
      Set<String> coveredIds,
      Map<String, Set<String>> coverageBySourceId) {
    return providerRegionIds.stream()
        .filter(regionId -> !selectedIds.contains(regionId))
        .max(
            Comparator.comparingInt(
                    (String regionId) ->
                        countNewCoverage(regionId, providerRegionIds, coveredIds, coverageBySourceId))
                .thenComparing(regionId -> regionId))
        .filter(regionId -> countNewCoverage(regionId, providerRegionIds, coveredIds, coverageBySourceId) > 0)
        .orElse(null);
  }

  private int countNewCoverage(
      String providerRegionId,
      Set<String> providerRegionIds,
      Set<String> coveredIds,
      Map<String, Set<String>> coverageBySourceId) {
    return (int)
        coverageBySourceId.getOrDefault(providerRegionId, Set.of(providerRegionId)).stream()
            .filter(providerRegionIds::contains)
            .filter(regionId -> !coveredIds.contains(regionId))
            .count();
  }

  private double percentage(int part, int total) {
    if (total == 0) {
      return 0;
    }
    return Math.round(part * 1000.0 / total) / 10.0;
  }
}
