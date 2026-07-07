# 당근여지도

지역 기반 중고거래 검색을 전국 단위로 확장하기 위한 **검색 거점 자동 최적화** 프로젝트입니다.

당근마켓처럼 사용자가 특정 지역을 기준으로 검색해야 하는 환경에서는 전국 매물을 찾으려면 지역을 직접 바꿔가며 반복 검색해야 합니다. 당근여지도는 사용자가 `맥북` 같은 검색어만 입력하면, 서버가 전국을 커버할 수 있는 검색 거점과 검색 반경을 자동으로 계산하고 결과를 통합해 보여주는 프로토타입입니다.

> 현재 외부 서비스 직접 연동은 하지 않고, 데이터 공급자는 `ListingSearchRepository` 인터페이스 뒤의 Mock 구현으로 분리했습니다. 실제 연동 시 외부 서비스 정책과 이용 약관을 준수하는 구현체로 교체하는 구조입니다.

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
2. 후보 검색 반경을 작은 값부터 평가한다.
3. 각 반경에서 전국을 커버할 수 있는 거점 경로를 만든다.
4. 새로 커버하는 지역이 많은 거점을 우선 선택한다.
5. 목표 거점 수 안에서 100% 커버되는 첫 반경을 최적 반경으로 선택한다.
6. 선택된 거점별 검색 결과를 통합해 응답한다.

현재 샘플 데이터 기준 `맥북` 검색 결과:

```text
optimizedRadiusKm = 50
searchRegionCount = 12
coveragePercent = 100.0
```

## 주요 기능

- 검색어만 입력하는 전국 검색 UI
- 검색 반경 자동 산출
- 전국 커버용 검색 거점 자동 선택
- 거점별 이동 경로와 커버리지 제공
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
- `NationwideSearchService`: 최적 반경과 검색 거점 경로 계산
- `RegionRepository`: 지역 앵커 조회
- `ListingSearchRepository`: 외부 검색 공급자 추상화
- `MockListingSearchRepository`: 현재 프로토타입용 목 검색 결과 생성

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

API:

```text
GET /api/search/nationwide?keyword=맥북
GET /api/search/regions
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
    "strategy": "AUTO_OPTIMIZED_RADIUS",
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
- 외부 검색 공급자와 경로 계산 로직을 분리해 교체 가능성을 확보했다.
- 검색 거점 선택 알고리즘의 한계와 개선 방향을 문서로 남겼다.
- 실제 개발 중 발생한 404, 루트 JSON 응답, H2 DevTools 경고를 트러블슈팅 문서로 정리했다.

## 문서

- [아키텍처](docs/architecture.md)
- [검색 거점 최적화](docs/search-optimization.md)
- [성능 개선 계획](docs/performance-plan.md)
- [트러블슈팅 기록](docs/troubleshooting.md)

