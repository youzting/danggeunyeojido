package com.example.karrotsearch.domain.search.dto.response;

import com.example.karrotsearch.domain.search.entity.ProviderRegion;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProviderRegionResponse {

  private final String providerRegionId;
  private final String providerRegionName;
  private final String providerName;
  private final String discoverySource;
  private final boolean active;
  private final LocalDateTime firstDiscoveredAt;
  private final LocalDateTime lastObservedAt;

  public static ProviderRegionResponse from(ProviderRegion providerRegion) {
    return ProviderRegionResponse.builder()
        .providerRegionId(providerRegion.getProviderRegionId())
        .providerRegionName(providerRegion.getProviderRegionName())
        .providerName(providerRegion.getProviderName())
        .discoverySource(providerRegion.getDiscoverySource())
        .active(providerRegion.isActive())
        .firstDiscoveredAt(providerRegion.getFirstDiscoveredAt())
        .lastObservedAt(providerRegion.getLastObservedAt())
        .build();
  }
}
