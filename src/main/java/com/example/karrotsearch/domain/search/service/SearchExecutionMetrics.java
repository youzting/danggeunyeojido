package com.example.karrotsearch.domain.search.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchExecutionMetrics {

  private final long planningTimeMs;
  private final long listingFetchTimeMs;
  private final long totalElapsedTimeMs;
  private final String listingProvider;
}
