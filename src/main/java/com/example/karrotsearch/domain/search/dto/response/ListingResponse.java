package com.example.karrotsearch.domain.search.dto.response;

import com.example.karrotsearch.domain.search.entity.SearchListing;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ListingResponse {

  private final String title;
  private final String price;
  private final String regionName;
  private final String searchedFrom;
  private final String postedAt;
  private final String url;

  public static ListingResponse from(SearchListing listing) {
    return ListingResponse.builder()
        .title(listing.getTitle())
        .price(listing.getPrice())
        .regionName(listing.getRegionName())
        .searchedFrom(listing.getSearchedFrom())
        .postedAt(listing.getPostedAt())
        .url(listing.getUrl())
        .build();
  }
}
