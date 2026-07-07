package com.example.karrotsearch.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.karrotsearch.domain.search.dto.request.NationwideSearchRequest;
import com.example.karrotsearch.domain.search.dto.response.RegionResponse;
import com.example.karrotsearch.domain.search.dto.response.NationwideSearchResponse;
import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.repository.ListingSearchRepository;
import com.example.karrotsearch.domain.search.repository.RegionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NationwideSearchServiceTests {

  @Autowired private NationwideSearchService nationwideSearchService;
  @Autowired private RegionRepository regionRepository;

  @Test
  void regionsExposeProviderRegionMappings() {
    List<RegionResponse> regions = nationwideSearchService.findRegions();

    assertThat(regions).hasSize(24);
    assertThat(regions)
        .allSatisfy(
            region -> {
              assertThat(region.getProviderRegionId()).isNotBlank();
              assertThat(region.getProviderRegionName()).isNotBlank();
            });
  }

  @Test
  void searchBuildsFiftyKmHubGridByDefault() {
    NationwideSearchRequest request = new NationwideSearchRequest();
    request.setKeyword("맥북");
    request.setStartRegionId("seoul-gangnam");

    NationwideSearchResponse response = nationwideSearchService.search(request);

    assertThat(response.getSteps()).isNotEmpty();
    assertThat(response.getOptimizedRadiusKm()).isEqualTo(50);
    assertThat(response.getStrategy()).isEqualTo("FIFTY_KM_HUB_GRID");
    assertThat(response.getSearchRegionCount()).isEqualTo(12);
    assertThat(response.getCoveragePercent()).isEqualTo(100.0);
    assertThat(response.getRemainingRegions()).isEmpty();
    assertThat(response.getListings()).isNotEmpty();
  }

  @Test
  void searchKeepsEveryProviderRegionWhenProviderDoesNotSupportDistanceCoverage() {
    NationwideSearchService exactProviderSearchService =
        new NationwideSearchService(regionRepository, new ExactProviderListingSearchRepository());
    NationwideSearchRequest request = new NationwideSearchRequest();
    request.setKeyword("맥북");
    request.setStartRegionId("seoul-gangnam");

    NationwideSearchResponse response = exactProviderSearchService.search(request);

    assertThat(response.getStrategy()).isEqualTo("FIFTY_KM_PROVIDER_REGION_HUBS");
    assertThat(response.getSteps()).hasSize(24);
    assertThat(response.getCoveragePercent()).isEqualTo(100.0);
    assertThat(response.getSteps())
        .extracting(step -> step.getRegion().getId())
        .contains("jeju-jeju", "jeju-seogwipo");
    assertThat(response.getSteps()).allSatisfy(step -> assertThat(step.getNewlyCoveredRegions()).isEqualTo(1));
  }

  @Test
  void searchMovesToUncoveredRegionsUntilNationwideCatalogIsCovered() {
    NationwideSearchRequest request = new NationwideSearchRequest();
    request.setKeyword("맥북");
    request.setStartRegionId("seoul-gangnam");
    request.setRadiusKm(80);
    request.setMaxStops(24);

    NationwideSearchResponse response = nationwideSearchService.search(request);

    assertThat(response.getSteps()).isNotEmpty();
    assertThat(response.getOptimizedRadiusKm()).isEqualTo(80);
    assertThat(response.getStrategy()).isEqualTo("MANUAL_RADIUS");
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

  private static class ExactProviderListingSearchRepository implements ListingSearchRepository {

    @Override
    public List<SearchListing> search(String keyword, SearchPlan plan) {
      return List.of();
    }

    @Override
    public String providerName() {
      return "exact-provider";
    }

    @Override
    public boolean supportsDistanceCoverage() {
      return false;
    }
  }
}
