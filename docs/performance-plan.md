# 성능 개선 계획

## 현재 병목 후보

현재 프로토타입은 지역 앵커 수가 작기 때문에 충분히 빠르다. 하지만 실제 전국 읍면동 또는 법정동 단위로 확장하면 다음 지점이 병목이 된다.

- 지역 수 증가에 따른 거리 계산 반복
- 후보 반경별 경로 재계산
- 거점별 외부 검색 요청 증가
- 중복 상품 제거 비용
- 외부 서비스 응답 지연과 실패 처리

## 1. 거리 계산 캐싱

현재는 `GeoDistanceCalculator.kilometersBetween()`을 필요한 시점마다 호출한다.

지역 앵커가 `N`개라면 거리 행렬을 미리 만들 수 있다.

```text
regionDistance[fromRegionId][toRegionId] = distanceKm
```

효과:

- 반복 삼각함수 계산 제거
- 후보 반경 평가 속도 개선
- 거점 선택 알고리즘 단순화

예상 적용 위치:

- `NationwideSearchService`
- 별도 `RegionDistanceService`

## 2. 반경 후보 평가 최적화

현재는 후보 반경을 순차 평가한다.

개선안:

- 작은 반경부터 평가하되 100% 커버가 불가능한 반경은 빠르게 중단
- 이전 반경 평가 결과를 다음 반경 평가에 재사용
- 운영 데이터 기준으로 자주 선택되는 반경을 우선 평가

현재 샘플 데이터에서는 `맥북` 검색 기준 다음 결과가 나온다.

```text
optimizedRadiusKm = 50
searchRegionCount = 12
coveragePercent = 100.0
```

## 3. 검색 거점 선택 알고리즘 개선

현재는 새로 커버하는 지역 수가 가장 큰 거점을 선택하는 탐욕 알고리즘이다.

현재 거점 선택 방식의 상세 기준은 [검색 거점 최적화](search-optimization.md)에 정리했다.

장점:

- 구현이 단순하다.
- 빠르게 좋은 해를 찾는다.
- 포트폴리오 프로토타입에서 설명하기 쉽다.

한계:

- 전체 이동거리 최적해를 보장하지 않는다.
- 실제 검색 품질은 인구 밀도와 거래량에 영향을 받는다.

개선 방향:

- 커버 지역 수 + 예상 거래량 + 이동 거리 페널티를 함께 점수화
- 지역별 검색 결과 밀도를 학습해 가중치 반영
- 큰 권역 단위 1차 탐색 후 미커버 지역만 2차 탐색

예시 점수:

```text
score = newlyCoveredCount * 10 + expectedListingDensity - moveDistanceKm * 0.03
```

## 4. 외부 검색 요청 병렬화

실제 검색 공급자가 연결되면 가장 큰 병목은 거점별 검색 요청이다.

개선안:

- 거점별 검색 요청을 비동기 병렬 처리
- 동시 요청 수 제한
- 실패한 거점만 재시도
- 공급자 rate limit 보호

예상 구조:

```text
NationwideSearchService
└── ListingSearchRepository
    └── AsyncKarrotListingSearchRepository
```

주의점:

- 외부 서비스 이용 약관 준수
- 요청 간격 제한
- 캐시와 중복 제거 필수

## 5. 결과 중복 제거

인접 거점 검색 시 같은 상품이 여러 번 나올 수 있다.

중복 제거 키 후보:

- 외부 상품 ID
- 상품 URL
- 제목 + 가격 + 지역 + 작성 시간 조합

실제 연동 시에는 외부 상품 ID 또는 canonical URL을 우선 사용한다.

## 6. 캐싱

검색어와 거점 조합은 반복될 가능성이 있다.

캐시 후보:

- 지역 앵커 목록
- 거리 행렬
- 검색어 + 거점 결과
- 최적 경로 계산 결과

캐시 키 예시:

```text
search:{keyword}:{regionId}:{radiusKm}
plan:{regionVersion}:{maxStops}:{targetCoverage}
```

운영 환경에서는 Redis 캐시를 고려할 수 있다.

## 7. 측정 지표

포트폴리오에서 성능 개선을 설명하려면 아래 지표를 남기는 것이 좋다.

- 경로 계산 시간
- 외부 검색 요청 총 개수
- 외부 검색 평균 응답 시간
- 캐시 hit ratio
- 중복 제거 전/후 결과 수
- 검색 전체 응답 시간 P50/P95/P99

추후 Micrometer와 Spring Boot Actuator를 추가하면 지표 수집이 쉬워진다.
