package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.Region;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchPlan {

  private final int radiusKm;
  private final String strategy;
  private final List<CoverageStep> steps;
  private final List<Region> coveredRegions;
  private final List<Region> remainingRegions;
  private final double coveragePercent;
  private final double totalMoveKm;
}
