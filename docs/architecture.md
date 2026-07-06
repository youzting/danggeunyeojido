# 아키텍처

## 목표

당근여지도는 사용자가 지역을 직접 바꾸며 검색하지 않아도, 서버가 전국 검색에 필요한 지역 거점을 자동으로 계산하는 것을 목표로 한다.

핵심 흐름은 다음과 같다.

1. 사용자가 검색어를 입력한다.
2. 서버가 지역 앵커 목록을 조회한다.
3. 후보 반경을 순서대로 평가한다.
4. 전국을 커버할 수 있는 검색 거점 경로를 만든다.
5. 각 거점 기준으로 검색 공급자를 호출한다.
6. 결과를 통합해 응답한다.

## 레이어 구조

프로젝트는 `dropshop` 스타일을 참고해 도메인 단위 3레이어 구조로 구성했다.

```text
domain/search
├── controller
├── dto
│   ├── request
│   └── response
├── entity
├── repository
└── service
```

## Controller

`SearchController`는 HTTP 요청과 응답 DTO 변환의 진입점이다.

- `GET /api/search/nationwide`
- `GET /api/search/regions`

컨트롤러는 검색 알고리즘을 직접 알지 않고 `NationwideSearchService`에 위임한다.

## Service

`NationwideSearchService`가 전국 검색 경로를 계산한다.

주요 책임:

- 자동 반경 최적화
- 검색 거점 선택
- 커버리지 계산
- 검색 공급자 호출
- 응답 DTO 구성

자동 반경 최적화는 현재 다음 후보를 평가한다.

```text
8, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100, 120, 150, 200km
```

후보 반경별로 검색 경로를 만들고, 다음 조건을 만족하는 첫 결과를 선택한다.

- 전국 커버 100%
- 검색 거점 수가 목표치 이하

현재 목표 거점 수는 `12`개다.

## Repository

`RegionRepository`는 JPA 기반 지역 앵커 저장소다.

`ListingSearchRepository`는 실제 검색 공급자를 추상화한다. 현재 구현체는 `MockListingSearchRepository`이며, 실제 데이터 연동 시 이 인터페이스 구현체만 교체하면 된다.

## Entity

`Region`은 검색 기준점으로 사용할 지역 앵커다.

주요 필드:

- `id`
- `name`
- `province`
- `latitude`
- `longitude`

`SearchListing`은 외부 검색 공급자에서 가져온 검색 결과를 표현한다. 현재는 JPA 엔티티가 아니라 도메인 결과 객체로 사용한다.

## UI

화면은 Thymeleaf가 아니라 Spring Boot 정적 리소스로 제공한다.

```text
src/main/resources/static/index.html
src/main/resources/static/css/app.css
src/main/resources/static/js/app.js
```

프론트엔드는 검색어만 서버에 전달하고, 서버가 반환한 최적 반경, 거점 수, 이동 경로, 검색 결과를 렌더링한다.

