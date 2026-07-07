package com.example.karrotsearch.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.karrotsearch.domain.search.dto.response.ProviderCoveragePlanResponse;
import com.example.karrotsearch.domain.search.dto.response.ProviderRegionResponse;
import com.example.karrotsearch.domain.search.entity.CoverageType;
import com.example.karrotsearch.domain.search.entity.RegionCoverage;
import com.example.karrotsearch.domain.search.repository.RegionCoverageRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProviderCoveragePlanningServiceTests {

  @Autowired private ProviderCoveragePlanningService providerCoveragePlanningService;
  @Autowired private RegionCoverageRepository regionCoverageRepository;

  @BeforeEach
  void clearObservedCoverage() {
    regionCoverageRepository.deleteAll();
  }

  @Test
  void providerRegionsAreSeededFromRegionMappings() {
    List<ProviderRegionResponse> providerRegions =
        providerCoveragePlanningService.findProviderRegions();

    assertThat(providerRegions).hasSize(24);
    assertThat(providerRegions)
        .extracting(ProviderRegionResponse::getProviderRegionId)
        .contains("6035", "6026", "3835");
  }

  @Test
  void coveragePlanPrefersHubThatCoversMoreProviderRegions() {
    regionCoverageRepository.save(
        RegionCoverage.create("6035", "역삼동", "237", "상암동", CoverageType.SIBLING, "맥북"));

    ProviderCoveragePlanResponse response = providerCoveragePlanningService.buildCoveragePlan(1);

    assertThat(response.getSelectedHubCount()).isEqualTo(1);
    assertThat(response.getHubs().get(0).getProviderRegionId()).isEqualTo("6035");
    assertThat(response.getHubs().get(0).getNewlyCoveredRegionCount()).isEqualTo(2);
  }
}
