package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.ProviderRegion;
import com.example.karrotsearch.domain.search.repository.ProviderRegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderRegionRecorder {

  private static final String PROVIDER_NAME = "daangn";

  private final ProviderRegionRepository providerRegionRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String providerRegionId, String providerRegionName, String discoverySource) {
    if (providerRegionId == null
        || providerRegionId.isBlank()
        || providerRegionName == null
        || providerRegionName.isBlank()) {
      return;
    }

    providerRegionRepository
        .findById(providerRegionId)
        .ifPresentOrElse(
            providerRegion -> providerRegion.observe(providerRegionName, discoverySource),
            () ->
                providerRegionRepository.save(
                    ProviderRegion.create(
                        providerRegionId, providerRegionName, PROVIDER_NAME, discoverySource)));
  }
}
