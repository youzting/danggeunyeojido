package com.example.karrotsearch.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.karrotsearch.domain.search.dto.request.NationwideSearchRequest;
import com.example.karrotsearch.domain.search.dto.response.NationwideSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NationwideSearchServiceTests {

  @Autowired private NationwideSearchService nationwideSearchService;

  @Test
  void searchMovesToUncoveredRegionsUntilNationwideCatalogIsCovered() {
    NationwideSearchRequest request = new NationwideSearchRequest();
    request.setKeyword("맥북");
    request.setStartRegionId("seoul-gangnam");
    request.setRadiusKm(80);
    request.setMaxStops(24);

    NationwideSearchResponse response = nationwideSearchService.search(request);

    assertThat(response.getSteps()).isNotEmpty();
    assertThat(response.getCoveragePercent()).isEqualTo(100.0);
    assertThat(response.getRemainingRegions()).isEmpty();
    assertThat(response.getListings()).isNotEmpty();
  }

  @Test
  void searchHonorsStopLimit() {
    NationwideSearchRequest request = new NationwideSearchRequest();
    request.setKeyword("맥북");
    request.setStartRegionId("seoul-gangnam");
    request.setRadiusKm(1);
    request.setMaxStops(3);

    NationwideSearchResponse response = nationwideSearchService.search(request);

    assertThat(response.getSteps()).hasSize(3);
    assertThat(response.getCoveragePercent()).isLessThan(100.0);
  }
}
