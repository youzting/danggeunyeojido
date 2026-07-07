package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.CoverageType;
import com.example.karrotsearch.domain.search.entity.RegionCoverage;
import com.example.karrotsearch.domain.search.repository.RegionCoverageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegionCoverageRecorder {

  private final RegionCoverageRepository regionCoverageRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      String sourceProviderRegionId,
      String sourceProviderRegionName,
      String coveredProviderRegionId,
      String coveredProviderRegionName,
      CoverageType coverageType,
      String keywordSample) {
    if (sourceProviderRegionId == null
        || sourceProviderRegionId.isBlank()
        || coveredProviderRegionId == null
        || coveredProviderRegionId.isBlank()) {
      return;
    }

    regionCoverageRepository
        .findBySourceProviderRegionIdAndCoveredProviderRegionId(
            sourceProviderRegionId, coveredProviderRegionId)
        .ifPresentOrElse(
            coverage ->
                coverage.observe(
                    sourceProviderRegionName, coveredProviderRegionName, coverageType, keywordSample),
            () ->
                regionCoverageRepository.save(
                    RegionCoverage.create(
                        sourceProviderRegionId,
                        sourceProviderRegionName,
                        coveredProviderRegionId,
                        coveredProviderRegionName,
                        coverageType,
                        keywordSample)));
  }
}
