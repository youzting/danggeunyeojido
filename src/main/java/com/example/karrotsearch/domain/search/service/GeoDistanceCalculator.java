package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.Region;

final class GeoDistanceCalculator {

  private static final double EARTH_RADIUS_KM = 6371.0088;

  private GeoDistanceCalculator() {}

  static double kilometersBetween(Region first, Region second) {
    double lat1 = Math.toRadians(first.getLatitude());
    double lat2 = Math.toRadians(second.getLatitude());
    double deltaLat = Math.toRadians(second.getLatitude() - first.getLatitude());
    double deltaLon = Math.toRadians(second.getLongitude() - first.getLongitude());

    double a =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(lat1)
                * Math.cos(lat2)
                * Math.sin(deltaLon / 2)
                * Math.sin(deltaLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return EARTH_RADIUS_KM * c;
  }
}
