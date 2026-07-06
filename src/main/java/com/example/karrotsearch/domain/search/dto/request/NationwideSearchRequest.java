package com.example.karrotsearch.domain.search.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NationwideSearchRequest {

  @NotBlank private String keyword;

  private String startRegionId;

  @Min(1)
  @Max(200)
  private Integer radiusKm;

  @Min(1)
  @Max(80)
  private Integer maxStops;
}
