package com.example.karrotsearch.domain.search.entity;

import com.example.karrotsearch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "region_coverages",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_region_coverages_source_covered",
          columnNames = {"sourceProviderRegionId", "coveredProviderRegionId"})
    },
    indexes = {
      @Index(name = "idx_region_coverages_source", columnList = "sourceProviderRegionId"),
      @Index(name = "idx_region_coverages_covered", columnList = "coveredProviderRegionId")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionCoverage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 50)
  private String sourceProviderRegionId;

  @Column(nullable = false, length = 50)
  private String sourceProviderRegionName;

  @Column(nullable = false, length = 50)
  private String coveredProviderRegionId;

  @Column(nullable = false, length = 50)
  private String coveredProviderRegionName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private CoverageType coverageType;

  @Column(length = 100)
  private String keywordSample;

  @Column(nullable = false)
  private LocalDateTime lastObservedAt;

  private RegionCoverage(
      String sourceProviderRegionId,
      String sourceProviderRegionName,
      String coveredProviderRegionId,
      String coveredProviderRegionName,
      CoverageType coverageType,
      String keywordSample,
      LocalDateTime lastObservedAt) {
    this.sourceProviderRegionId = sourceProviderRegionId;
    this.sourceProviderRegionName = sourceProviderRegionName;
    this.coveredProviderRegionId = coveredProviderRegionId;
    this.coveredProviderRegionName = coveredProviderRegionName;
    this.coverageType = coverageType;
    this.keywordSample = keywordSample;
    this.lastObservedAt = lastObservedAt;
  }

  public static RegionCoverage create(
      String sourceProviderRegionId,
      String sourceProviderRegionName,
      String coveredProviderRegionId,
      String coveredProviderRegionName,
      CoverageType coverageType,
      String keywordSample) {
    return new RegionCoverage(
        sourceProviderRegionId,
        sourceProviderRegionName,
        coveredProviderRegionId,
        coveredProviderRegionName,
        coverageType,
        keywordSample,
        LocalDateTime.now());
  }

  public void observe(
      String sourceProviderRegionName,
      String coveredProviderRegionName,
      CoverageType coverageType,
      String keywordSample) {
    this.sourceProviderRegionName = sourceProviderRegionName;
    this.coveredProviderRegionName = coveredProviderRegionName;
    this.coverageType = coverageType;
    this.keywordSample = keywordSample;
    this.lastObservedAt = LocalDateTime.now();
  }
}
