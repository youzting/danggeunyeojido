package com.example.karrotsearch.domain.search.repository;

import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.service.SearchPlan;
import java.util.List;

/** 당근 지역 검색 결과 공급자. 실제 연동 시 이 인터페이스 구현체만 교체한다. */
public interface ListingSearchRepository {

  List<SearchListing> search(String keyword, SearchPlan plan);

  default String providerName() {
    return "unknown";
  }

  default boolean supportsDistanceCoverage() {
    return true;
  }
}
