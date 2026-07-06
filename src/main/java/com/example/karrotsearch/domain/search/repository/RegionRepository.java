package com.example.karrotsearch.domain.search.repository;

import com.example.karrotsearch.domain.search.entity.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 지역 앵커 저장소. */
public interface RegionRepository extends JpaRepository<Region, String> {

  List<Region> findAllByOrderByProvinceAscNameAsc();
}
