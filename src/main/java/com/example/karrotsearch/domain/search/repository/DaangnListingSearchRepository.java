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

  @Value("${search.provider.daangn.max-results-per-region:0}")
  private int maxResultsPerRegion;

  @Override
  public List<SearchListing> search(String keyword, SearchPlan plan) {
    Map<String, SearchListing> deduplicated = new LinkedHashMap<>();

    for (CoverageStep step : plan.getSteps()) {
      Region region = step.getRegion();
      DaangnRegion daangnRegion = resolveRegion(region);
      if (daangnRegion == null) {
        continue;
      }

      for (SearchListing listing : searchRegion(keyword, region, daangnRegion, step.getSequence())) {
        deduplicated.putIfAbsent(listing.getUrl(), listing);
      }
    }

    return List.copyOf(deduplicated.values());
  }

  @Override
  public String providerName() {
    return "daangn-public-web";
  }

  private DaangnRegion resolveRegion(Region region) {
    if (hasProviderRegion(region)) {
      return new DaangnRegion(region.getProviderRegionId(), region.getProviderRegionName());
    }

    for (String keyword : regionKeywords(region)) {
      try {
        String url = BASE_URL + "/kr/api/v1/regions/keyword?keyword=" + encode(keyword);
        String body = readBody(url);
        if (body == null || body.isBlank()) {
          continue;
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode locations = root.path("locations");
        if (!locations.isArray() || locations.isEmpty()) {
          continue;
        }

        JsonNode first = locations.get(0);
        return new DaangnRegion(first.path("id").asText(), first.path("name").asText());
      } catch (Exception e) {
        log.debug("당근 지역 해석 실패. region={}, keyword={}", region.getName(), keyword, e);
      }
    }

    log.warn("당근 지역 해석 결과 없음. region={}", region.getName());
    return null;
  }

  private boolean hasProviderRegion(Region region) {
    return hasText(region.getProviderRegionId()) && hasText(region.getProviderRegionName());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private List<SearchListing> searchRegion(
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

      String body = readBody(url);
      if (body == null || body.isBlank()) {
        log.warn("당근 검색 응답 본문 없음. keyword={}, region={}", keyword, sourceRegion.getName());
        return List.of();
      }

      JsonNode root = objectMapper.readTree(body);
      JsonNode articles = root.path("allPage").path("fleamarketArticles");
      if (!articles.isArray()) {
        return List.of();
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
      return listings;
    } catch (Exception e) {
      log.warn("당근 검색 실패. keyword={}, region={}", keyword, sourceRegion.getName(), e);
      return List.of();
    }
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
        .searchedFrom(sequence + "번째 거점 · " + sourceRegion.getName())
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

  private String readBody(String url) {
    try {
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
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("당근 HTTP 응답 실패. status={}, url={}", response.statusCode(), url);
        return null;
      }
      return response.body();
    } catch (Exception e) {
      log.warn("당근 HTTP 요청 실패. url={}", url, e);
      return null;
    }
  }

  private List<String> regionKeywords(Region region) {
    String name = region.getName();
    List<String> keywords = new ArrayList<>();
    keywords.add(name);

    String[] parts = name.split(" ");
    if (parts.length > 1) {
      keywords.add(parts[parts.length - 1]);
    }

    return keywords.stream().distinct().toList();
  }

  private record DaangnRegion(String id, String name) {}
}
