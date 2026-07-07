package com.example.karrotsearch.domain.search.repository;

import com.example.karrotsearch.domain.search.entity.Region;
import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.service.CoverageStep;
import com.example.karrotsearch.domain.search.service.SearchPlan;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!daangn")
public class MockListingSearchRepository implements ListingSearchRepository {

  private static final NumberFormat PRICE_FORMAT = NumberFormat.getNumberInstance(Locale.KOREA);

  @Override
  public List<SearchListing> search(String keyword, SearchPlan plan) {
    List<SearchListing> listings = new ArrayList<>();
    for (CoverageStep step : plan.getSteps()) {
      if (step.getNewlyCoveredRegions() == 0) {
        continue;
      }
      listings.add(createListing(keyword, step.getRegion(), step.getSequence(), "상태 좋은"));
      listings.add(createListing(keyword, step.getRegion(), step.getSequence(), "급처"));
    }
    return listings;
  }

  @Override
  public String providerName() {
    return "mock";
  }

  private SearchListing createListing(String keyword, Region region, int sequence, String prefix) {
    int seed = Math.abs((keyword + region.getId() + prefix).hashCode());
    int price = ((seed % 45) + 5) * 10000;

    return SearchListing.builder()
        .title(prefix + " " + keyword)
        .price(PRICE_FORMAT.format(price) + "원")
        .priceValue((long) price)
        .regionName(region.getName())
        .searchedFrom(sequence + "번째 이동 지역")
        .postedAt((sequence % 6 + 1) + "시간 전")
        .url("https://www.daangn.com/search/" + keyword.replace(" ", "%20"))
        .directBuy(false)
        .build();
  }
}
