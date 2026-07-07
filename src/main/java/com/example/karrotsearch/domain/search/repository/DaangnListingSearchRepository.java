package com.example.karrotsearch.domain.search.repository;

import com.example.karrotsearch.domain.search.entity.Region;
import com.example.karrotsearch.domain.search.entity.SearchListing;
import com.example.karrotsearch.domain.search.service.CoverageStep;
import com.example.karrotsearch.domain.search.service.SearchPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("daangn")
@RequiredArgsConstructor
@Slf4j
public class DaangnListingSearchRepository implements ListingSearchRepository {

  private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,###");
  private static final String BASE_URL = "https://www.daangn.com";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private final Map<String, DaangnRegion> scrapedRegionCache = new ConcurrentHashMap<>();

  @Value("${search.provider.daangn.max-results-per-region:0}")
  private int maxResultsPerRegion;

  @Value("${search.provider.daangn.request-delay-ms:300}")
  private long requestDelayMs;

  @Value("${search.provider.daangn.stop-on-rate-limit:true}")
  private boolean stopOnRateLimit;

  @Value("${search.provider.daangn.scrape-region-hubs:true}")
  private boolean scrapeRegionHubs;

  @Value("${search.provider.daangn.expand-sibling-regions:true}")
  private boolean expandSiblingRegions;

  @Value("${search.provider.daangn.max-expanded-region-requests:80}")
  private int maxExpandedRegionRequests;

  @Override
  public List<SearchListing> search(String keyword, SearchPlan plan) {
    Map<String, SearchListing> deduplicated = new LinkedHashMap<>();
    Map<String, DaangnRegion> searchedRegions = new LinkedHashMap<>();
    int expandedRegionRequests = 0;
    boolean expansionBudgetExhausted = false;

    for (CoverageStep step : plan.getSteps()) {
      Region region = step.getRegion();
      DaangnRegion daangnRegion = resolveRegion(region);
      if (daangnRegion == null) {
        continue;
      }
      searchedRegions.putIfAbsent(daangnRegion.id(), daangnRegion);

      ScrapeResult scrapeResult = searchRegion(keyword, region, daangnRegion, step.getSequence());
      for (SearchListing listing : scrapeResult.listings()) {
        deduplicated.putIfAbsent(listing.getUrl(), listing);
      }
      if (scrapeResult.rateLimited() && stopOnRateLimit) {
        log.warn("당근 스크래퍼 요청 제한으로 남은 거점 검색을 중단합니다. keyword={}", keyword);
        break;
      }
      if (!expandSiblingRegions || expansionBudgetExhausted) {
        continue;
      }

      for (DaangnRegion siblingRegion : scrapeResult.siblingRegions()) {
        if (searchedRegions.containsKey(siblingRegion.id())) {
          continue;
        }
        if (maxExpandedRegionRequests > 0 && expandedRegionRequests >= maxExpandedRegionRequests) {
          log.info(
              "당근 주변 동네 확장 검색 상한에 도달했습니다. keyword={}, maxExpandedRegionRequests={}",
              keyword,
              maxExpandedRegionRequests);
          expansionBudgetExhausted = true;
          break;
        }

        searchedRegions.put(siblingRegion.id(), siblingRegion);
        expandedRegionRequests++;
        ScrapeResult siblingScrapeResult =
            searchRegion(keyword, region, siblingRegion, step.getSequence());
        for (SearchListing listing : siblingScrapeResult.listings()) {
          deduplicated.putIfAbsent(listing.getUrl(), listing);
        }
        if (siblingScrapeResult.rateLimited() && stopOnRateLimit) {
          log.warn("당근 스크래퍼 요청 제한으로 주변 동네 확장 검색을 중단합니다. keyword={}", keyword);
          return List.copyOf(deduplicated.values());
        }
      }
    }

    return List.copyOf(deduplicated.values());
  }

  @Override
  public String providerName() {
    return "daangn-public-web-scraper";
  }

  @Override
  public boolean supportsDistanceCoverage() {
    return false;
  }

  private DaangnRegion resolveRegion(Region region) {
    if (scrapeRegionHubs) {
      DaangnRegion scraped = scrapedRegionCache.computeIfAbsent(region.getId(), id -> scrapeRegion(region));
      if (scraped != null) {
        return scraped;
      }
    }

    if (hasProviderRegion(region)) {
      return new DaangnRegion(region.getProviderRegionId(), region.getProviderRegionName());
    }

    return scrapeRegion(region);
  }

  private DaangnRegion scrapeRegion(Region region) {
    for (String keyword : regionKeywords(region)) {
      try {
        String url = BASE_URL + "/kr/api/v1/regions/keyword?keyword=" + encode(keyword);
        ScrapeResponse response = readBody(url);
        if (response.rateLimited()) {
          return null;
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
          continue;
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode locations = root.path("locations");
        if (!locations.isArray() || locations.isEmpty()) {
          continue;
        }

        JsonNode location = findBestLocation(region, locations);
        if (location != null) {
          DaangnRegion daangnRegion =
              new DaangnRegion(location.path("id").asText(), location.path("name").asText());
          log.debug(
              "당근 거점 스크랩 완료. region={}, keyword={}, providerRegion={}-{}",
              region.getName(),
              keyword,
              daangnRegion.name(),
              daangnRegion.id());
          return daangnRegion;
        }
      } catch (Exception e) {
        log.debug("당근 지역 해석 실패. region={}, keyword={}", region.getName(), keyword, e);
      }
    }

    log.warn("당근 지역 해석 결과 없음. region={}", region.getName());
    return null;
  }

  private JsonNode findBestLocation(Region region, JsonNode locations) {
    String regionName = region.getName();
    String[] parts = regionName.split(" ");
    String districtName = parts.length > 1 ? parts[parts.length - 1] : regionName;

    JsonNode sameDistrict = null;
    JsonNode sameProviderName = null;
    for (JsonNode location : locations) {
      if (location.path("depth").asInt() != 3) {
        continue;
      }
      String name = location.path("name").asText("");
      String name2 = location.path("name2").asText("");
      if (hasProviderRegion(region) && name.equals(region.getProviderRegionName())) {
        sameProviderName = location;
      }
      if (!districtName.isBlank() && name2.equals(districtName)) {
        sameDistrict = location;
      }
    }

    if (sameProviderName != null) {
      return sameProviderName;
    }
    if (hasProviderRegion(region)) {
      return null;
    }
    if (sameDistrict != null) {
      return sameDistrict;
    }

    JsonNode first = locations.get(0);
    return first != null && first.path("depth").asInt() == 3 ? first : null;
  }

  private boolean hasProviderRegion(Region region) {
    return hasText(region.getProviderRegionId()) && hasText(region.getProviderRegionName());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private ScrapeResult searchRegion(
      String keyword, Region sourceRegion, DaangnRegion daangnRegion, int sequence) {
    try {
      String regionParam = daangnRegion.name() + "-" + daangnRegion.id();
      String url =
          BASE_URL
              + "/kr/buy-sell/all/?in="
              + encode(regionParam)
              + "&search="
              + encode(keyword)
              + "&only_on_sale=true&_data=routes/kr.buy-sell._index";

      ScrapeResponse response = readBody(url);
      if (response.rateLimited()) {
        return new ScrapeResult(List.of(), List.of(), true);
      }
      String body = response.body();
      if (body == null || body.isBlank()) {
        log.warn("당근 검색 응답 본문 없음. keyword={}, region={}", keyword, sourceRegion.getName());
        return new ScrapeResult(List.of(), List.of(), false);
      }

      JsonNode root = objectMapper.readTree(body);
      JsonNode articles = root.path("allPage").path("fleamarketArticles");
      if (!articles.isArray()) {
        return new ScrapeResult(List.of(), siblingRegions(root, daangnRegion), false);
      }

      List<SearchListing> listings = new ArrayList<>();
      for (JsonNode article : articles) {
        if (maxResultsPerRegion > 0 && listings.size() >= maxResultsPerRegion) {
          break;
        }
        if (sequence > 1 && isDirectBuyListing(article)) {
          continue;
        }
        listings.add(toListing(article, sourceRegion, daangnRegion, sequence));
      }
      return new ScrapeResult(listings, siblingRegions(root, daangnRegion), false);
    } catch (Exception e) {
      log.warn("당근 검색 실패. keyword={}, region={}", keyword, sourceRegion.getName(), e);
      return new ScrapeResult(List.of(), List.of(), false);
    }
  }

  private List<DaangnRegion> siblingRegions(JsonNode root, DaangnRegion currentRegion) {
    JsonNode siblings = root.path("regionFilterOptions").path("siblingRegions");
    if (!siblings.isArray()) {
      return List.of();
    }

    List<DaangnRegion> regions = new ArrayList<>();
    for (JsonNode sibling : siblings) {
      if (sibling.path("depth").asInt() != 3) {
        continue;
      }
      String id = sibling.path("id").asText("");
      String name = sibling.path("name").asText("");
      if (id.isBlank() || name.isBlank() || id.equals(currentRegion.id())) {
        continue;
      }
      regions.add(new DaangnRegion(id, name));
    }
    return regions;
  }

  private boolean isDirectBuyListing(JsonNode article) {
    return hasTruthyField(
            article,
            "isDirectBuy",
            "directBuy",
            "isBuyable",
            "buyable",
            "isShipping",
            "shippingAvailable",
            "isDelivery",
            "deliveryAvailable")
        || hasMetadataText(article, "바로구매")
        || hasMetadataText(article, "direct_buy")
        || hasMetadataText(article, "shipping");
  }

  private boolean hasTruthyField(JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode value = node.path(fieldName);
      if (value.isBoolean() && value.asBoolean()) {
        return true;
      }
      if (value.isTextual() && "true".equalsIgnoreCase(value.asText())) {
        return true;
      }
    }
    return false;
  }

  private boolean hasMetadataText(JsonNode node, String expectedText) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }
    if (node.isTextual()) {
      return node.asText("").toLowerCase().contains(expectedText.toLowerCase());
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        if (hasMetadataText(child, expectedText)) {
          return true;
        }
      }
      return false;
    }
    if (node.isObject()) {
      for (String fieldName :
          List.of(
              "badges",
              "badge",
              "labels",
              "label",
              "tags",
              "tag",
              "tradeType",
              "tradeMethod",
              "delivery",
              "shipping",
              "commerce",
              "payment")) {
        if (hasMetadataText(node.path(fieldName), expectedText)) {
          return true;
        }
      }
    }
    return false;
  }

  private SearchListing toListing(
      JsonNode article, Region sourceRegion, DaangnRegion daangnRegion, int sequence) {
    String href = article.path("href").asText("");
    String price = formatPrice(article.path("price").asText(""));
    String articleRegion = article.path("region").path("name").asText(daangnRegion.name());

    return SearchListing.builder()
        .title(article.path("title").asText("제목 없음"))
        .price(price)
        .regionName(articleRegion)
        .searchedFrom(sequence + "번째 거점 · " + sourceRegion.getName() + " · " + daangnRegion.name())
        .postedAt(formatPostedAt(article.path("createdAt").asText("")))
        .url(href.isBlank() ? BASE_URL : href)
        .build();
  }

  private String formatPrice(String rawPrice) {
    if (rawPrice == null || rawPrice.isBlank()) {
      return "가격 정보 없음";
    }
    try {
      long price = Math.round(Double.parseDouble(rawPrice));
      if (price <= 0) {
        return "나눔";
      }
      return PRICE_FORMAT.format(price) + "원";
    } catch (NumberFormatException e) {
      return rawPrice;
    }
  }

  private String formatPostedAt(String createdAt) {
    if (createdAt == null || createdAt.isBlank()) {
      return "작성일 정보 없음";
    }
    try {
      return OffsetDateTime.parse(createdAt).toLocalDate().toString();
    } catch (DateTimeParseException e) {
      return createdAt;
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private ScrapeResponse readBody(String url) {
    try {
      waitBeforeRequest();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .header("Accept", "application/json,text/plain,*/*")
              .header("Cache-Control", "no-cache")
              .header("Referer", BASE_URL + "/kr/buy-sell/")
              .header("User-Agent", "Mozilla/5.0 danggeunyeojido-readonly-prototype")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 403 || response.statusCode() == 429) {
        log.warn("당근 스크래퍼 요청 제한 감지. status={}, url={}", response.statusCode(), url);
        return new ScrapeResponse(response.statusCode(), null);
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("당근 HTTP 응답 실패. status={}, url={}", response.statusCode(), url);
        return new ScrapeResponse(response.statusCode(), null);
      }
      return new ScrapeResponse(response.statusCode(), response.body());
    } catch (Exception e) {
      log.warn("당근 HTTP 요청 실패. url={}", url, e);
      return new ScrapeResponse(0, null);
    }
  }

  private void waitBeforeRequest() {
    if (requestDelayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(requestDelayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private List<String> regionKeywords(Region region) {
    String name = region.getName();
    List<String> keywords = new ArrayList<>();
    keywords.add(name);

    String[] parts = name.split(" ");
    if (parts.length > 1) {
      if (hasProviderRegion(region)) {
        keywords.add(parts[parts.length - 1] + " " + region.getProviderRegionName());
      }
      keywords.add(parts[parts.length - 1]);
    }
    if (hasProviderRegion(region)) {
      keywords.add(region.getProviderRegionName());
    }

    return keywords.stream().distinct().toList();
  }

  private record ScrapeResponse(int statusCode, String body) {

    private boolean rateLimited() {
      return statusCode == 403 || statusCode == 429;
    }
  }

  private record ScrapeResult(
      List<SearchListing> listings, List<DaangnRegion> siblingRegions, boolean rateLimited) {}

  private record DaangnRegion(String id, String name) {}
}
