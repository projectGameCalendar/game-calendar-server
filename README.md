# ProjectGC

ProjectGC는 Kotlin과 Spring Boot로 구축한 게임 출시 캘린더 웹 서비스입니다.
현재 단계의 목표는 이후 기능 확장과 마이크로서비스 전환을 염두에 둔 최소한의 안정적인 뼈대를 마련하는 것입니다.


## 개발 환경
- Java 21
- Kotlin 1.9
- Spring Boot 3.5.6
- Gradle 8
- PostgreSQL 17


## 디렉터리 구조
- `src/main/kotlin/com/projectgc/calendar`
  - 웹 API와 직접 연결된 도메인(컨트롤러, 서비스, 영속성 등)
- `src/main/kotlin/com/projectgc/batch` 
  - 배치/예약 작업용 컴포넌트(향후 분리 대비)
- `src/main/resources/db/migration` 
  - Flyway 마이그레이션 스크립트 보관 위치


## 개발 준비
1. JDK 21 설치
2. PostgreSQL 17 설치 및 DB 생성
3. `application.yml.sample`을 복사해 로컬 `application.yml`을 만든 뒤 DB 접속 정보 및 IGDB 키 입력
   - IGDB 키는 [Twitch Developer Console](https://dev.twitch.tv/console)에서 발급
4. 애플리케이션 실행 시 Flyway가 자동으로 마이그레이션 스크립트를 순서대로 실행
5. Gradle 의존성 다운로드: `./gradlew dependencies`


## 주요 명령어

- 전체 빌드: `./gradlew clean build`
- 테스트만 실행: `./gradlew test`
- 애플리케이션 실행: `./gradlew bootRun`


## Service API
- `GET /api/releases?from=YYYY-MM-DD&to=YYYY-MM-DD` — 출시 일정 범위 조회
- `GET /api/releases?date=YYYY-MM-DD` — 단일 날짜 출시 일정 조회
- `GET /api/games/{gameId}` — 게임 상세 조회

`GET /api/releases`는 `platformGroup=PC,NINTENDO` 또는 반복 파라미터 형식의 플랫폼 그룹 필터를 지원합니다.


## 문제 해결 가이드
- Gradle 경고가 나타나면 `./gradlew build --warning-mode=all`로 세부 정보를 확인하세요.
- IDE가 Gradle과 동일하게 JDK 21을 사용하도록 설정하세요.
