# 당근여지도

지역 기반 중고거래 검색을 전국 단위로 확장하기 위한 **검색 거점 자동 최적화** 프로젝트입니다.

당근마켓처럼 사용자가 특정 지역을 기준으로 검색해야 하는 환경에서는 전국 매물을 찾으려면 지역을 직접 바꿔가며 반복 검색해야 합니다. 당근여지도는 사용자가 `맥북` 같은 검색어만 입력하면, 서버가 전국을 50km 단위로 커버할 수 있는 검색 거점을 생성하고 결과를 통합해 보여주는 프로토타입입니다.

> 기본 실행은 Mock 공급자를 사용합니다. `daangn` 프로필에서는 공식 API가 아니라 공개 웹 응답을 읽는 실험용 스크래퍼로 교체되며, 외부 서비스 정책과 이용 약관을 준수하는 범위에서 포트폴리오 성능 측정 용도로 사용합니다.

## 문제 정의

지역 검색 서비스에서 전국 검색을 하려면 다음 문제가 생깁니다.

- 사용자가 지역을 수동으로 여러 번 변경해야 한다.
- 너무 촘촘하게 검색하면 요청 수가 많아진다.
- 너무 넓게 검색하면 지역 검색 품질이 떨어질 수 있다.
- 인접 지역 검색 결과가 중복될 수 있다.
- 전국 커버 여부를 사용자가 직접 판단하기 어렵다.

이 프로젝트는 이 문제를 **전국 커버에 필요한 최소 수준의 검색 거점 선택 문제**로 보고 해결합니다.

## 해결 아이디어

1. 전국 주요 지역을 `Region` 앵커로 저장한다.
2. 기본 검색 간격을 50km로 둔다.
3. 50km 단위로 전국을 커버할 수 있는 거점 경로를 만든다.
4. 새로 커버하는 지역이 많은 거점을 우선 선택한다.
5. 50km 거점 경로로 100% 커버되는 지역을 계산한다.
6. 선택된 거점별 검색 결과를 통합해 응답한다.

`daangn` 프로필에서는 거리 반경만으로 실제 검색 범위를 판단하지 않는다. 공개 웹 응답에서 관측한 형제 동네와 검색 결과 동네를 `RegionCoverage`로 저장하고, 이후 검색 계획에서 이 관측 커버리지 맵을 greedy set cover 점수로 활용한다. 관측 데이터가 부족한 초기 상태에서는 전국으로 빠르게 퍼지는 거리 분산 전략을 fallback으로 사용한다.

검색 중 발견한 당근 동네 ID/name은 `ProviderRegion` 마스터 데이터로 함께 저장한다. 이 데이터와 `RegionCoverage`를 합치면 현재까지 관측된 동네 중 어떤 동네를 대표 거점으로 검색해야 가장 많은 미커버 동네를 덮는지 계산할 수 있다.

현재 샘플 데이터 기준 `맥북` 검색 결과:

```text
optimizedRadiusKm = 50
strategy = FIFTY_KM_HUB_GRID
searchRegionCount = 12
coveragePercent = 100.0
```

## 주요 기능

- 검색어만 입력하는 전국 검색 UI
- 50km 단위 전국 검색 거점 생성
- 전국 커버용 검색 거점 자동 선택
- 거점별 이동 경로와 커버리지 제공
- 당근 공개 응답 기반 관측 커버리지 맵 적재
- 검색 중 발견한 당근 동네 ID/name 마스터 누적
- 관측 커버리지 기반 greedy set cover 거점 선택
- 실제 공급자 검색을 위한 지역별 대표 동네 id/name 매핑
- REST API 기반 3레이어 아키텍처
- 실제 검색 공급자 교체를 위한 Repository 인터페이스 분리
- 포트폴리오 설명용 아키텍처, 성능 개선, 트러블슈팅 문서화

## 기술 스택

- Java 17
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- H2 Database
- Gradle
- Vanilla HTML/CSS/JavaScript

## 아키텍처

`dropshop` 프로젝트 스타일을 참고해 도메인 단위 3레이어 구조로 구성했습니다.

```text
src/main/java/com/example/karrotsearch
├── common
│   ├── dto
│   └── entity
└── domain/search
    ├── controller
    ├── dto
    │   ├── request
    │   └── response
    ├── entity
    ├── repository
    └── service
```

핵심 클래스:

- `SearchController`: 검색 API 진입점
- `NationwideSearchService`: 50km 단위 검색 거점 경로 계산
- `RegionRepository`: 지역 앵커 조회
- `ListingSearchRepository`: 외부 검색 공급자 추상화
- `MockListingSearchRepository`: 현재 프로토타입용 목 검색 결과 생성
- `DaangnListingSearchRepository`: `daangn` 프로필에서 공개 웹 응답 기반 스크래핑 결과 수집

## 실행

```bash
./gradlew bootRun
```

Windows:

```bash
.\gradlew.bat bootRun
```

웹 페이지:

```text
http://localhost:8080/
```

실제 공개 웹 데이터 공급자 프로필:

```bash
SPRING_PROFILES_ACTIVE=daangn SERVER_PORT=8081 ./gradlew bootRun
```

Windows:

```cmd
set SPRING_PROFILES_ACTIVE=daangn&& set SERVER_PORT=8081&& gradlew.bat bootRun
```

`search.provider.daangn.max-results-per-region=0`이면 지역별 응답 페이지에 포함된 결과를 자르지 않고 모두 수집합니다. 양수로 바꾸면 성능 테스트나 디버깅을 위해 지역별 결과 수를 제한할 수 있습니다.

스크래퍼 보호 설정:

```properties
search.provider.daangn.request-delay-ms=300
search.provider.daangn.stop-on-rate-limit=true
search.provider.daangn.scrape-region-hubs=true
search.provider.daangn.expand-sibling-regions=true
search.provider.daangn.max-expanded-region-requests=120
search.provider.daangn.max-sibling-regions-per-hub=6
search.provider.daangn.cache-ttl-minutes=30
```

`scrape-region-hubs=true`이면 검색 거점의 실제 당근 동네를 공개 지역 검색 응답으로 먼저 해석하고, 실패하면 저장된 대표 동네 매핑으로 fallback합니다. `403` 또는 `429`가 감지되면 해당 검색의 남은 거점 요청을 중단합니다.

`expand-sibling-regions=true`이면 거점 검색 응답에 포함된 같은 시군구의 형제 동네를 추가로 순회합니다. 예를 들어 `부산 해운대구 · 우동` 거점은 우동만 검색하지 않고 좌동, 중동, 재송동, 반여동 같은 주변 동네를 추가 검색한 뒤 URL 기준으로 중복 제거합니다. `max-sibling-regions-per-hub`는 특정 거점이 확장 요청을 독점하지 않도록 거점별 확장 수를 제한하고, `max-expanded-region-requests=0`이면 전체 확장 요청 수 제한을 두지 않습니다.

웹 검색 응답에서 매물의 실제 동네가 검색 기준 동네와 다르면 `OBSERVED_RESULT` 커버리지로 저장합니다. `cache-ttl-minutes` 동안 같은 검색어와 당근 동네 ID의 공개 웹 응답 본문을 재사용해 반복 요청을 줄입니다.

검색 과정에서 발견한 당근 동네는 `provider_regions` 테이블에 누적됩니다. `/api/search/provider-hubs`는 이 동네 마스터와 관측 커버리지를 바탕으로 현재 데이터 기준 대표 거점 후보를 greedy 방식으로 계산합니다.

API:

```text
GET /api/search/nationwide?keyword=맥북
GET /api/search/regions
GET /api/search/provider-regions
GET /api/search/coverage
GET /api/search/provider-hubs?maxHubs=50
```

테스트:

```bash
./gradlew test
```

Windows:

```bash
.\gradlew.bat test
```

## API 응답 예시

```json
{
  "success": true,
  "code": 200,
  "data": {
    "optimizedRadiusKm": 50,
    "strategy": "FIFTY_KM_HUB_GRID",
    "coveragePercent": 100.0,
    "totalMoveKm": 1483.5,
    "searchRegionCount": 12,
    "steps": [],
    "listings": []
  }
}
```

## 포트폴리오 포인트

- 단순 CRUD가 아니라 검색 커버리지 최적화 문제를 도메인 문제로 정의했다.
- 공식 API가 없는 외부 검색 공급자를 공개 웹 스크래퍼로 분리해 교체 가능성을 확보했다.
- 검색 거점 선택 알고리즘의 한계와 개선 방향을 문서로 남겼다.
- 실제 개발 중 발생한 404, 루트 JSON 응답, H2 DevTools 경고를 트러블슈팅 문서로 정리했다.
- Mock 공급자와 실제 공개 웹 데이터 공급자 기준 검색 속도를 분리 측정했다.
- 전국 법정동 단위 감사로 대표 거점 검색의 한계와 rate limit 대응 방향을 정리했다.

## 문서

- [아키텍처](docs/architecture.md)
- [검색 거점 최적화](docs/search-optimization.md)
- [성능 측정 결과](docs/performance-results.md)
- [성능 개선 계획](docs/performance-plan.md)
- [트러블슈팅 기록](docs/troubleshooting.md)
