# 당근여지도

당근여지도는 지역 기반 검색만 제공되는 중고거래 서비스를 전국 단위로 탐색하기 위한 검색 거점 자동 최적화 프로토타입입니다.

사용자는 `맥북`처럼 검색어만 입력합니다. 서버는 지역 앵커 데이터를 기준으로 전국 커버에 필요한 검색 거점과 최적 반경을 계산하고, 각 거점을 순회하며 검색한 결과를 하나의 전국 검색 결과처럼 제공합니다.

## 핵심 기능

- 검색어만 입력하는 전국 검색 UI
- 전국 커버용 지역 앵커 자동 선택
- 후보 검색 반경 평가를 통한 최적 반경 산출
- 새로 커버되는 지역 수가 큰 거점을 우선 선택하는 탐욕 기반 순회 알고리즘
- 실제 검색 공급자 교체를 고려한 `ListingSearchRepository` 분리
- Spring Boot 3레이어 구조 적용

## 기술 스택

- Java 17
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- H2 Database
- Gradle
- Vanilla HTML/CSS/JavaScript

## 구조

```text
src/main/java/com/example/karrotsearch
├── common
│   ├── dto
│   └── entity
└── domain/search
    ├── controller
    ├── dto
    ├── entity
    ├── repository
    └── service
```

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

## 현재 동작 예시

샘플 지역 앵커 기준으로 `맥북`을 검색하면 서버가 자동으로 다음 값을 계산합니다.

- 최적 반경: 50km
- 검색 거점: 12개
- 전국 커버리지: 100%

현재 검색 결과는 실제 당근마켓 데이터가 아니라 `MockListingSearchRepository`에서 생성하는 목 데이터입니다. 실제 연동 시에는 외부 서비스 정책과 이용 약관을 준수하는 데이터 공급자를 별도 구현체로 교체하는 구조입니다.

## 문서

- [아키텍처](docs/architecture.md)
- [성능 개선 계획](docs/performance-plan.md)
- [트러블슈팅 기록](docs/troubleshooting.md)

