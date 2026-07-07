package com.example.karrotsearch.domain.search.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProviderCoveragePlanResponse {

  private final int totalProviderRegionCount;
  private final int selectedHubCount;
  private final double coveragePercent;
  private final List<ProviderCoverageHubResponse> hubs;
  private final List<ProviderRegionResponse> remainingRegions;
}
