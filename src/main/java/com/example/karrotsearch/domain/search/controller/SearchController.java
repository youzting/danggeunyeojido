package com.example.karrotsearch.domain.search.controller;

import com.example.karrotsearch.common.dto.ApiResponse;
import com.example.karrotsearch.domain.search.dto.request.NationwideSearchRequest;
import com.example.karrotsearch.domain.search.dto.response.NationwideSearchResponse;
import com.example.karrotsearch.domain.search.dto.response.RegionCoverageResponse;
import com.example.karrotsearch.domain.search.dto.response.RegionResponse;
import com.example.karrotsearch.domain.search.service.NationwideSearchService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController {

  private final NationwideSearchService nationwideSearchService;

  @GetMapping("/")
  public ResponseEntity<ApiResponse<Map<String, String>>> getApiGuide() {
    Map<String, String> guide = new LinkedHashMap<>();
    guide.put("regions", "GET /api/search/regions");
    guide.put("coverage", "GET /api/search/coverage");
    guide.put(
        "nationwide",
        "GET /api/search/nationwide?keyword=맥북&startRegionId=seoul-gangnam&radiusKm=80&maxStops=24");
    return ResponseEntity.ok(ApiResponse.ok(guide));
  }

  @GetMapping("/regions")
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getRegions() {
    return ResponseEntity.ok(ApiResponse.ok(nationwideSearchService.findRegions()));
  }

  @GetMapping("/coverage")
  public ResponseEntity<ApiResponse<List<RegionCoverageResponse>>> getObservedCoverages() {
    return ResponseEntity.ok(ApiResponse.ok(nationwideSearchService.findObservedCoverages()));
  }

  @GetMapping("/nationwide")
  public ResponseEntity<ApiResponse<NationwideSearchResponse>> searchNationwide(
      @Valid @ModelAttribute NationwideSearchRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(nationwideSearchService.search(request)));
  }
}
