package com.example.karrotsearch.domain.search.entity;

import com.example.karrotsearch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "provider_regions",
    indexes = {
      @Index(name = "idx_provider_regions_name", columnList = "providerRegionName"),
      @Index(name = "idx_provider_regions_observed", columnList = "lastObservedAt")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderRegion extends BaseEntity {

  @Id
  @Column(length = 50)
  private String providerRegionId;

  @Column(nullable = false, length = 50)
  private String providerRegionName;

  @Column(nullable = false, length = 50)
  private String providerName;

  @Column(nullable = false, length = 50)
  private String discoverySource;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private LocalDateTime firstDiscoveredAt;

  @Column(nullable = false)
  private LocalDateTime lastObservedAt;

  private ProviderRegion(
      String providerRegionId,
      String providerRegionName,
      String providerName,
      String discoverySource,
      LocalDateTime observedAt) {
    this.providerRegionId = providerRegionId;
    this.providerRegionName = providerRegionName;
    this.providerName = providerName;
    this.discoverySource = discoverySource;
    this.active = true;
    this.firstDiscoveredAt = observedAt;
    this.lastObservedAt = observedAt;
  }

  public static ProviderRegion create(
      String providerRegionId, String providerRegionName, String providerName, String discoverySource) {
    return new ProviderRegion(
        providerRegionId, providerRegionName, providerName, discoverySource, LocalDateTime.now());
  }

  public void observe(String providerRegionName, String discoverySource) {
    this.providerRegionName = providerRegionName;
    this.discoverySource = discoverySource;
    this.active = true;
    this.lastObservedAt = LocalDateTime.now();
  }
}
