package com.example.karrotsearch.domain.search.entity;

import lombok.Builder;
import lombok.Getter;

/** 외부 검색 공급자에서 가져온 상품 검색 결과. */
@Getter
@Builder
public class SearchListing {

  private final String title;
  private final String price;
  private final String regionName;
  private final String searchedFrom;
  private final String postedAt;
  private final String url;
}
