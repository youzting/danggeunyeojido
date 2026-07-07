package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.Region;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RegionDistanceMatrix {

  private final Map<String, Map<String, Double>> distances = new HashMap<>();

  RegionDistanceMatrix(List<Region> regions) {
    for (Region from : regions) {
      Map<String, Double> row = new HashMap<>();
      for (Region to : regions) {
        row.put(to.getId(), GeoDistanceCalculator.kilometersBetween(from, to));
      }
      distances.put(from.getId(), row);
    }
  }

  double distance(Region from, Region to) {
    return distances.getOrDefault(from.getId(), Map.of()).getOrDefault(to.getId(), 0.0);
  }
}
