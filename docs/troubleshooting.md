# 트러블슈팅 기록

## 1. Thymeleaf 화면 구조가 프로젝트 방향과 맞지 않음

### 증상

초기 구현은 Thymeleaf 템플릿 중심이었다.

하지만 목표 구조는 `dropshop`과 유사한 3레이어 아키텍처였다.

### 원인

프로토타입을 빠르게 만들기 위해 서버 렌더링 화면부터 붙였지만, 포트폴리오와 실제 확장 관점에서는 REST API와 서비스 레이어 분리가 더 적합했다.

### 해결

Thymeleaf 의존성을 제거하고 다음 구조로 재편했다.

```text
controller
service
repository
entity
dto
```

화면은 `src/main/resources/static` 아래 정적 HTML/CSS/JS로 제공하고, 데이터는 REST API로 조회하도록 변경했다.

## 2. 루트 경로에서 404 발생

### 증상

`http://localhost:8080/` 접속 시 404 또는 Whitelabel Error Page가 표시됐다.

### 원인

REST API 구조로 바꾸면서 루트 페이지를 제공하는 정적 `index.html`이 없었다.

### 해결

다음 파일을 추가했다.

```text
src/main/resources/static/index.html
src/main/resources/static/css/app.css
src/main/resources/static/js/app.js
```

Spring Boot가 `static/index.html`을 welcome page로 인식하도록 했다.

## 3. 루트가 페이지가 아니라 JSON을 반환

### 증상

`http://localhost:8080/`에서 페이지가 아니라 API 안내 JSON이 반환됐다.

### 원인

임시로 만든 `HomeController`가 `/` 요청을 먼저 처리했다. 이후 파일은 삭제했지만, 실행 중인 DevTools 서버가 삭제 전 클래스 파일을 잡고 있어 같은 증상이 계속 보였다.

### 해결

1. `HomeController` 제거
2. 실행 중인 Java 프로세스 종료
3. `./gradlew clean test` 실행
4. 서버 재시작

확인 로그:

```text
Adding welcome page: class path resource [static/index.html]
```

## 4. H2 종료 경고

### 증상

DevTools 재시작 중 다음 경고가 출력됐다.

```text
Database is already closed
```

### 원인

인메모리 H2 데이터베이스가 JVM 종료 시점에 먼저 닫히고, Spring이 다시 종료 처리를 시도하면서 경고가 발생했다.

### 해결

JDBC URL에 옵션을 추가했다.

```properties
spring.datasource.url=jdbc:h2:mem:karrotsearch;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=FALSE
```

## 5. 검색 UX가 수동 플래너처럼 보임

### 증상

초기 화면은 검색어 외에도 시작 지역, 검색 반경, 최대 이동 수를 사용자가 직접 입력해야 했다.

이는 “검색어만 입력하면 전국 검색이 되는 서비스”라는 목표와 달랐다.

### 원인

알고리즘 검증을 위해 내부 파라미터를 화면에 노출했다.

### 해결

화면에서는 검색어만 받도록 변경했다.

서버는 `radiusKm`가 없으면 자동 최적화 모드로 동작한다.

```text
GET /api/search/nationwide?keyword=맥북
```

현재 샘플 기준 결과:

```text
optimizedRadiusKm = 50
searchRegionCount = 12
coveragePercent = 100.0
```

## 6. 실제 당근마켓 데이터 연동 전 목 데이터 사용

### 배경

당근마켓 전국 검색은 공식 공개 API가 안정적으로 제공되는 전제가 아니다.

### 결정

외부 데이터 연동을 바로 구현하지 않고 `ListingSearchRepository` 인터페이스를 먼저 만들었다.

현재 구현체:

```text
MockListingSearchRepository
```

실제 연동 시 교체 지점:

```text
ListingSearchRepository
└── KarrotListingSearchRepository
```

### 이유

- 외부 서비스 약관과 접근 정책을 확인하기 전까지 무리한 크롤링을 피한다.
- 검색 경로 최적화 로직과 데이터 공급자 로직을 분리한다.
- 테스트 가능한 백엔드 구조를 먼저 만든다.

