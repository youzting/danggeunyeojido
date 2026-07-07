package com.example.karrotsearch.domain.search.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProviderCoverageHubResponse {

  private final int sequence;
  private final String providerRegionId;
  private final String providerRegionName;
  private final int coveredRegionCount;
  private final int newlyCoveredRegionCount;
  private final List<ProviderRegionResponse> newlyCoveredRegions;
}
