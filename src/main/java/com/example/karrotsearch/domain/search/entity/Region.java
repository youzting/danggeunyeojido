package com.example.karrotsearch.domain.search.entity;

import com.example.karrotsearch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 당근 지역 검색의 기준점으로 사용할 지역 앵커. */
@Getter
@Entity
@Table(
    name = "regions",
    indexes = {
      @Index(name = "idx_regions_province_name", columnList = "province, name"),
      @Index(name = "idx_regions_location", columnList = "latitude, longitude")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseEntity {

  @Id
  @Column(length = 50)
  private String id;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 30)
  private String province;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(length = 50)
  private String providerRegionId;

  @Column(length = 50)
  private String providerRegionName;

  private Region(
      String id,
      String name,
      String province,
      double latitude,
      double longitude,
      String providerRegionId,
      String providerRegionName) {
    this.id = id;
    this.name = name;
    this.province = province;
    this.latitude = latitude;
    this.longitude = longitude;
    this.providerRegionId = providerRegionId;
    this.providerRegionName = providerRegionName;
  }

  public static Region create(
      String id, String name, String province, double latitude, double longitude) {
    return new Region(id, name, province, latitude, longitude, null, null);
  }

  public static Region create(
      String id,
      String name,
      String province,
      double latitude,
      double longitude,
      String providerRegionId,
      String providerRegionName) {
    return new Region(
        id, name, province, latitude, longitude, providerRegionId, providerRegionName);
  }
}
