package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.Region;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CoverageStep {

  private final int sequence;
  private final Region region;
  private final double distanceFromPreviousKm;
  private final int newlyCoveredRegions;
  private final int totalCoveredRegions;
  private final double coveragePercent;
}
