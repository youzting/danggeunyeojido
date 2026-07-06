package com.example.karrotsearch.domain.search.dto.response;

import com.example.karrotsearch.domain.search.service.CoverageStep;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchStepResponse {

  private final int sequence;
  private final RegionResponse region;
  private final double distanceFromPreviousKm;
  private final int newlyCoveredRegions;
  private final int totalCoveredRegions;
  private final double coveragePercent;

  public static SearchStepResponse from(CoverageStep step) {
    return SearchStepResponse.builder()
        .sequence(step.getSequence())
        .region(RegionResponse.from(step.getRegion()))
        .distanceFromPreviousKm(step.getDistanceFromPreviousKm())
        .newlyCoveredRegions(step.getNewlyCoveredRegions())
        .totalCoveredRegions(step.getTotalCoveredRegions())
        .coveragePercent(step.getCoveragePercent())
        .build();
  }
}
