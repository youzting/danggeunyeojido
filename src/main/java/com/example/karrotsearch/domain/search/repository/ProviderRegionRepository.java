package com.example.karrotsearch.domain.search.repository;

import com.example.karrotsearch.domain.search.entity.ProviderRegion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRegionRepository extends JpaRepository<ProviderRegion, String> {

  List<ProviderRegion> findAllByOrderByProviderRegionNameAsc();
}
