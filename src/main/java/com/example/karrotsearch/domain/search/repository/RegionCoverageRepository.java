package com.example.karrotsearch.domain.search.repository;

import com.example.karrotsearch.domain.search.entity.RegionCoverage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionCoverageRepository extends JpaRepository<RegionCoverage, Long> {

  Optional<RegionCoverage> findBySourceProviderRegionIdAndCoveredProviderRegionId(
      String sourceProviderRegionId, String coveredProviderRegionId);

  List<RegionCoverage> findBySourceProviderRegionIdIn(Collection<String> sourceProviderRegionIds);
}
