package com.example.karrotsearch.domain.search.dto.response;

import com.example.karrotsearch.domain.search.entity.Region;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegionResponse {

  private final String id;
  private final String name;
  private final String province;
  private final double latitude;
  private final double longitude;

  public static RegionResponse from(Region region) {
    return RegionResponse.builder()
        .id(region.getId())
        .name(region.getName())
        .province(region.getProvince())
        .latitude(region.getLatitude())
        .longitude(region.getLongitude())
        .build();
  }
}
