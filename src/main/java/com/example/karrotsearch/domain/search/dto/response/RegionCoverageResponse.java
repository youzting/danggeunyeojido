package com.example.karrotsearch.domain.search.dto.response;

import com.example.karrotsearch.domain.search.entity.CoverageType;
import com.example.karrotsearch.domain.search.entity.RegionCoverage;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegionCoverageResponse {

  private final String sourceProviderRegionId;
  private final String sourceProviderRegionName;
  private final String coveredProviderRegionId;
  private final String coveredProviderRegionName;
  private final CoverageType coverageType;
  private final String keywordSample;
  private final LocalDateTime lastObservedAt;

  public static RegionCoverageResponse from(RegionCoverage coverage) {
    return RegionCoverageResponse.builder()
        .sourceProviderRegionId(coverage.getSourceProviderRegionId())
        .sourceProviderRegionName(coverage.getSourceProviderRegionName())
        .coveredProviderRegionId(coverage.getCoveredProviderRegionId())
        .coveredProviderRegionName(coverage.getCoveredProviderRegionName())
        .coverageType(coverage.getCoverageType())
        .keywordSample(coverage.getKeywordSample())
        .lastObservedAt(coverage.getLastObservedAt())
        .build();
  }
}
