package com.example.karrotsearch.domain.search.service;

import com.example.karrotsearch.domain.search.entity.ProviderRegion;
import com.example.karrotsearch.domain.search.entity.Region;
import com.example.karrotsearch.domain.search.repository.ProviderRegionRepository;
import com.example.karrotsearch.domain.search.repository.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegionSeedService implements ApplicationRunner {

  private final RegionRepository regionRepository;
  private final ProviderRegionRepository providerRegionRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (regionRepository.count() == 0) {
      regionRepository.saveAll(seedRegions());
    }

    if (providerRegionRepository.count() == 0) {
      seedProviderRegions(regionRepository.findAll());
    }
  }

  private List<Region> seedRegions() {
    return List.of(
            Region.create("seoul-gangnam", "서울 강남구", "서울", 37.5172, 127.0473, "6035", "역삼동"),
            Region.create("seoul-jongno", "서울 종로구", "서울", 37.5735, 126.9788, "6", "부암동"),
            Region.create("seoul-mapo", "서울 마포구", "서울", 37.5663, 126.9016, "237", "상암동"),
            Region.create("incheon-bupyeong", "인천 부평구", "인천", 37.5070, 126.7219, "6519", "부평동"),
            Region.create("gyeonggi-suwon", "경기 수원시", "경기", 37.2636, 127.0286, "1291", "인계동"),
            Region.create("gyeonggi-seongnam", "경기 성남시", "경기", 37.4200, 127.1265, "1339", "정자동"),
            Region.create("gyeonggi-goyang", "경기 고양시", "경기", 37.6584, 126.8320, "1568", "대화동"),
            Region.create("gangwon-chuncheon", "강원 춘천시", "강원", 37.8813, 127.7298, "1898", "퇴계동"),
            Region.create("gangwon-gangneung", "강원 강릉시", "강원", 37.7519, 128.8761, "4253", "교동"),
            Region.create("chungbuk-cheongju", "충북 청주시", "충북", 36.6424, 127.4890, "2134", "오창읍"),
            Region.create("chungnam-cheonan", "충남 천안시", "충남", 36.8151, 127.1139, "7119", "두정동"),
            Region.create("daejeon-seogu", "대전 서구", "대전", 36.3554, 127.3839, "5793", "둔산동"),
            Region.create("sejong", "세종시", "세종", 36.4800, 127.2890, "1243", "조치원읍"),
            Region.create("jeonbuk-jeonju", "전북 전주시", "전북", 35.8242, 127.1480, "2499", "서신동"),
            Region.create("jeonnam-mokpo", "전남 목포시", "전남", 34.8118, 126.3922, "2766", "상동"),
            Region.create("gwangju-bukgu", "광주 북구", "광주", 35.1740, 126.9117, "1051", "용봉동"),
            Region.create("gyeongbuk-pohang", "경북 포항시", "경북", 36.0190, 129.3435, "3121", "죽도동"),
            Region.create("daegu-suseong", "대구 수성구", "대구", 35.8582, 128.6306, "5663", "범어동"),
            Region.create("ulsan-namgu", "울산 남구", "울산", 35.5438, 129.3301, "1201", "삼산동"),
            Region.create("busan-haeundae", "부산 해운대구", "부산", 35.1631, 129.1635, "6026", "우동"),
            Region.create("gyeongnam-changwon", "경남 창원시", "경남", 35.2285, 128.6811, "3478", "상남동"),
            Region.create("gyeongnam-jinju", "경남 진주시", "경남", 35.1800, 128.1076, "3560", "평거동"),
            Region.create("jeju-jeju", "제주 제주시", "제주", 33.4996, 126.5312, "3835", "노형동"),
            Region.create("jeju-seogwipo", "제주 서귀포시", "제주", 33.2539, 126.5596, "3840", "대정읍"));
  }

  private void seedProviderRegions(List<Region> regions) {
    for (Region region : regions) {
      if (region.getProviderRegionId() == null
          || region.getProviderRegionId().isBlank()
          || region.getProviderRegionName() == null
          || region.getProviderRegionName().isBlank()) {
        continue;
      }
      providerRegionRepository.save(
          ProviderRegion.create(
              region.getProviderRegionId(), region.getProviderRegionName(), "daangn", "region-seed"));
    }
  }
}
