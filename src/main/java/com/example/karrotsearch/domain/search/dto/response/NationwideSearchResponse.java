package com.example.karrotsearch.domain.search.dto.response;

import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.service.SearchPlan;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NationwideSearchResponse {

  private final int optimizedRadiusKm;
  private final String strategy;
  private final double coveragePercent;
  private final double totalMoveKm;
  private final int searchRegionCount;
  private final List<SearchStepResponse> steps;
  private final List<RegionResponse> coveredRegions;
  private final List<RegionResponse> remainingRegions;
  private final List<ListingResponse> listings;

  public static NationwideSearchResponse of(SearchPlan plan, List<SearchListing> listings) {
    return NationwideSearchResponse.builder()
        .optimizedRadiusKm(plan.getRadiusKm())
        .strategy(plan.getStrategy())
        .coveragePercent(plan.getCoveragePercent())
        .totalMoveKm(plan.getTotalMoveKm())
        .searchRegionCount(plan.getSteps().size())
        .steps(plan.getSteps().stream().map(SearchStepResponse::from).toList())
        .coveredRegions(plan.getCoveredRegions().stream().map(RegionResponse::from).toList())
        .remainingRegions(plan.getRemainingRegions().stream().map(RegionResponse::from).toList())
        .listings(listings.stream().map(ListingResponse::from).toList())
        .build();
  }
}
