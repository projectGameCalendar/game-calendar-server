# Service API 기획 및 구현 계획 요약

## 목적

이 문서는 게임 출시 캘린더 서비스의 `service` API 구현 전에 확정한 제품 방향, API 범위, 데이터 노출 정책, 응답 규칙을 정리한 기록이다.

현재 단계의 목표는 프론트 화면 구조를 과하게 고정하지 않고, 백엔드가 `service` 스키마의 출시/게임 데이터를 안정적으로 제공할 수 있는 최소 API를 정의하는 것이다.

## 제품 방향

- 이 프로젝트는 게임 출시 캘린더 서버다.
- 사용자는 코어 게이머를 주요 타깃으로 하되, 개인 프로젝트/취미용 서비스 성격을 가진다.
- 기본 경험은 범용적인 주요 출시작 중심이다.
- 향후 사용자의 관심 플랫폼/장르/지역에 따른 개인화와 추천을 고려하지만, 현 단계에서는 범용 API를 우선한다.
- 화면은 큰 방향에서 `캘린더`와 `게임 상세`만 고려한다.
- 별도 게임 상세 페이지는 현 단계 범위가 아니다. 상세 UI가 팝업, 모달, 전체 화면 등으로 구현되는지는 프론트 영역이다.

## 비범위

현 단계에서 제외한다.

- 검색 API
- 행사/쇼케이스 일정
- 날짜 미정 또는 월 단위 출시 정보 API
- 전체 지역별 출시 일정 제공
- 성인/민감 콘텐츠 필터링
- 상세 페이지
- 스크린샷/아트워크 전체 제공
- 관련 게임, 키워드, 테마, 모드 제공
- 알림, 계정 기반 관심 게임 저장
- 관리자 override

## API 범위

MVP service API는 아래 두 리소스로 시작한다.

```http
GET /api/releases
GET /api/games/{gameId}
```

### `GET /api/releases`

출시 일정 목록 API다. 리스트 리소스이므로 wrapper를 사용한다.

지원 조회 모드:

```http
GET /api/releases?from=2026-05-01&to=2026-06-30
GET /api/releases?date=2026-05-10
```

규칙:

- `from`, `to`는 함께 있어야 한다.
- `date`는 단일 날짜 조회다.
- `date`와 `from/to`는 동시에 사용할 수 없다.
- 아무 날짜 조건도 없으면 `400 Bad Request`.
- `from > to`이면 `400 Bad Request`.
- `from`, `to`, `date` 형식은 `YYYY-MM-DD`.
- 범위 조회는 `from`, `to` 모두 포함한다.
- 범위 조회 최대 길이는 62일이다.
- 날짜 기준 타임존은 `Asia/Seoul` 고정이다.
- 응답은 날짜별 그룹이 아니라 flat release item 배열이다.
- 빈 결과는 `200 OK`와 빈 `releases` 배열이다.

범위 조회 응답:

```json
{
  "from": "2026-05-01",
  "to": "2026-06-30",
  "count": 12,
  "releases": []
}
```

단일 날짜 조회 응답:

```json
{
  "date": "2026-05-10",
  "count": 3,
  "releases": []
}
```

`count`는 응답에 포함된 `releases` 개수다.

### `GET /api/games/{gameId}`

게임 상세 API다. 단일 리소스이므로 wrapper를 사용하지 않는다.

규칙:

- `gameId`는 `service.game.id`를 그대로 사용한다.
- 현재 `service.game.id`는 IGDB game id다.
- 게임이 없으면 `404 Not Found`.
- 게임 row가 있어도 표시 제목을 만들 수 없으면 `404 Not Found`.
- 한국/글로벌 출시 정보가 없어도 게임 자체가 유효하면 `200 OK`.

## 필터

### `platformGroup`

`GET /api/releases`는 `platformGroup` 필터를 지원한다.

예:

```http
GET /api/releases?from=2026-05-01&to=2026-06-30&platformGroup=pc,nintendo
GET /api/releases?date=2026-05-10&platformGroup=PC&platformGroup=NINTENDO
```

규칙:

- 콤마 구분과 반복 파라미터를 모두 허용한다.
- 대소문자를 구분하지 않는다.
- 공백은 trim한다.
- 중복은 제거한다.
- 알 수 없는 값이 하나라도 있으면 `400 Bad Request`.
- `platformGroup`은 요청 파라미터로만 유지한다.
- 응답에는 플랫폼 그룹 값을 포함하지 않는다.
- 내부 매핑은 서버 코드에서 처리한다.
- 필터는 병합 전 `service.game_release` row에 먼저 적용한다.
- 필터가 있으면 필터에 맞는 플랫폼 row만 남긴 뒤 병합한다.
- 필터가 있으면 플랫폼 미정/그룹 미정 항목은 제외한다.
- 필터가 없으면 서버는 플랫폼 기준으로 임의 제외하지 않는다.

지원 값:

- `PC`
- `PLAYSTATION`
- `NINTENDO`
- `XBOX`
- `MOBILE`

## 릴리즈 병합 단위

이 프로젝트의 캘린더 기본 단위는 `날짜 + 게임`이다.

`GET /api/releases`는 DB의 `service.game_release` row를 그대로 반환하지 않고, 아래 기준으로 병합한 release item을 반환한다.

```text
date + gameId + selectedRegion + releaseStatus
```

병합되는 값:

- 여러 `service.game_release.id`
- 여러 플랫폼

따라서 응답에는 단일 `releaseId`가 아니라 `releaseIds` 배열을 사용한다.

```json
{
  "releaseIds": [100, 101, 102]
}
```

항목이 하나여도 리스트 성격이면 배열로 반환한다.

## 지역 정책

릴리즈 API는 한국 기준 서비스로 시작한다.

정책:

- 한국 출시가 있으면 한국 출시만 반환한다.
- 한국 출시가 없으면 글로벌 출시를 fallback으로 반환한다.
- 한국/글로벌 외 지역은 현 단계 릴리즈 API에서 제외한다.
- 같은 게임/플랫폼에 한국과 글로벌 출시일이 서로 다르면 한국 날짜만 반환한다.
- 응답에는 선택된 `region`만 포함한다.
- 전체 지역별 출시 일정은 현 단계에서 제공하지 않는다.

식별:

- `service.release_region.id` 우선.
- `service.release_region.name` fallback.
- 서버 내부 매핑으로 한국/글로벌을 판단한다.
- 응답에는 별도 `region.code`를 포함하지 않는다.
- 응답은 DB 값 중심으로 `id`, `name`만 사용한다.

## 데이터 제외 및 누락 처리

서버 기본 제외 정책은 최소화한다.

릴리즈 API에서 제외:

- 게임 없음
- 표시 제목 없음
- 출시일 없음
- 날짜 범위 밖
- 한국/글로벌 외 지역
- 명시적으로 취소/신뢰 낮음이라고 판별 가능한 일정

부분 누락 처리:

- 플랫폼 참조 누락
- 지역 참조 누락
- 출시 상태 참조 누락
- 게임 타입 참조 누락
- 커버 참조/URL 누락
- 장르/회사/언어/링크/영상 일부 누락

위 항목은 가능한 경우 `null` 또는 빈 리스트로 응답하고, 서버 warning log에 남긴다.

warning log에는 가능한 식별자를 포함한다.

- `releaseIds`
- `releaseId`
- `gameId`
- 누락된 참조 ID
- 문제 유형

API 응답에는 `warnings` 필드를 포함하지 않는다.

## 응답 값 규칙

공통 규칙:

- JSON 필드는 camelCase.
- 단일 값 또는 단일 객체가 없으면 `null`.
- 리스트 값 또는 리스트 객체가 없으면 `[]`.
- 필드 생략은 기본적으로 하지 않는다.
- 리스트 성격의 값은 항목이 하나여도 배열로 반환한다.

리소스 규칙:

- 리스트형 API는 결과가 없어도 `200 OK`와 빈 리스트.
- 단일 리소스 API는 리소스가 없으면 `404 Not Found`.
- 잘못된 요청은 `400 Bad Request`.

## 오류 응답

간단한 공통 오류 포맷을 사용한다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "from must be before or equal to to"
}
```

기본 오류 코드:

- `INVALID_REQUEST`
  - 날짜 형식 오류
  - 날짜 조건 누락
  - `date`와 `from/to` 동시 사용
  - 범위 초과
  - 알 수 없는 `platformGroup`
- `NOT_FOUND`
  - 단일 리소스 없음
- `INTERNAL_ERROR`
  - 예상하지 못한 서버 오류

## 릴리즈 API 필드

`GET /api/releases`의 release item 필드는 아래로 확정한다.

- `releaseIds`
- `gameId`
- `date`
- `title`
- `defaultTitle`
- `gameType`
- `region`
- `releaseStatus`
- `platforms`
- `coverThumbnailUrl`
- `displayScore`
- `koreanLanguageSupport`

제외:

- `summary`
- `slug`
- `genres`
- `developers`
- `websites`
- `video`
- `gameStatus`
- `storyline`
- screenshots/artworks
- platform group 응답 필드
- region code 응답 필드

### 릴리즈 아이템 필드 규칙

`releaseIds`:

- 병합된 `service.game_release.id` 목록.
- 하나여도 배열.

`gameId`:

- `service.game.id`.

`date`:

- `release_date`를 `Asia/Seoul` 기준 `YYYY-MM-DD`로 변환.

`title`:

- 한국 로컬라이제이션 제목 우선.
- 없으면 `service.game.name`.

`defaultTitle`:

- `service.game.name`.
- 항상 채운다.
- `title`과 같을 수 있다.

`gameType`:

- `service.game_type` 기준.
- DB 컬럼명에 맞춰 `type` 사용.
- 없으면 `null`.

```json
{
  "id": 1,
  "type": "Main Game"
}
```

`region`:

- 선택된 출시 지역.
- `service.release_region` 기준.
- `id`, `name`만 포함.
- 없으면 `null`.

```json
{
  "id": 8,
  "name": "Korea"
}
```

`releaseStatus`:

- `service.release_status` 기준.
- `id`, `name`만 포함.
- `description`은 포함하지 않는다.
- 없으면 `null`.

```json
{
  "id": 1,
  "name": "Full Release"
}
```

`platforms`:

- 병합된 플랫폼 목록.
- `service.platform` 기준.
- `id`, `name`, `abbreviation`만 포함.
- 플랫폼 그룹은 응답하지 않는다.
- 없으면 `[]`.

```json
{
  "id": 6,
  "name": "PC",
  "abbreviation": "PC"
}
```

`coverThumbnailUrl`:

- 캘린더/리스트용 썸네일 URL.
- 한국 로컬라이제이션 커버 우선.
- 없으면 메인 커버.
- 없으면 아무 커버.
- 없으면 `null`.
- 서버가 `image_id` 또는 기존 URL 기반으로 용도별 URL을 생성한다.

`displayScore`:

- 정수 `0~100`.
- 캘린더/리스트 표시 및 정렬을 위한 서버 추천 점수.
- 절대적인 인기 점수는 아니다.
- 게임 상세 API에는 포함하지 않는다.

`koreanLanguageSupport`:

- 한국어 지원 정보.
- 상세 API와 동일 구조.
- 없으면 `null`.

```json
{
  "audio": false,
  "subtitles": true,
  "interface": true
}
```

## 릴리즈 API 정렬

범위 조회:

```text
date ASC
displayScore DESC
title ASC
releaseIds[0] ASC
```

단일 날짜 조회:

```text
displayScore DESC
title ASC
releaseIds[0] ASC
```

## `displayScore` 정책

MVP에서는 단순 규칙 기반으로 서버에서 계산한다.

반영 후보:

- 게임 타입
- 출시 상태
- 출시 지역
- 플랫폼 존재 여부
- 커버 존재 여부
- 요약 존재 여부
- 한국 로컬라이제이션 존재 여부
- 한국어 지원 여부
- 플랫폼 미정/그룹 미정 감점

한국어 지원 여부는 `displayScore`에 반영한다.

점수 계산은 구현 단계에서 상수 기반으로 시작하고, 테스트는 절대 점수보다 상대 순위를 중심으로 검증한다.

## 게임 상세 API 필드

`GET /api/games/{gameId}` 필드는 아래로 확정한다.

- `gameId`
- `slug`
- `title`
- `defaultTitle`
- `summary`
- `firstReleaseDate`
- `gameType`
- `gameStatus`
- `coverThumbnailUrl`
- `coverUrl`
- `platforms`
- `genres`
- `developers`
- `koreanLanguageSupport`
- `websites`
- `video`

제외:

- 출시 일정 목록
- `releaseStatus`
- `region`
- `storyline`
- screenshots/artworks
- publisher
- related games
- keywords/themes/modes
- `displayScore`

### 게임 상세 필드 규칙

`gameId`:

- `service.game.id`.

`slug`:

- `service.game.slug`.
- 없으면 `null`.

`title`:

- 한국 로컬라이제이션 제목 우선.
- 없으면 `service.game.name`.

`defaultTitle`:

- `service.game.name`.
- 항상 채운다.

`summary`:

- `service.game.summary`.
- 원문 그대로 반환.
- 없으면 `null`.
- 서버에서 길이를 자르지 않는다.

`firstReleaseDate`:

- `service.game.first_release_date`.
- `Asia/Seoul` 기준 `YYYY-MM-DD`.
- 없으면 `null`.

`gameType`:

- `service.game_type`.
- 없으면 `null`.
- 구조는 릴리즈 API와 동일.

`gameStatus`:

- `service.game_status`.
- 없으면 `null`.

```json
{
  "id": 1,
  "status": "Released"
}
```

`coverThumbnailUrl`, `coverUrl`:

- 한국 로컬라이제이션 커버 우선.
- 없으면 메인 커버.
- 없으면 아무 커버.
- 없으면 `null`.
- 서버가 용도별 URL을 생성한다.

`platforms`:

- `service.game_release.platform_id` 기준.
- 지역 fallback 정책 적용.
- 한국 출시 플랫폼 우선.
- 한국 출시 정보가 없는 플랫폼은 글로벌 출시 플랫폼 fallback.
- 한국/글로벌 외 지역만 있는 플랫폼은 제외.
- `platformGroup` 필터와 무관하게 동작한다.
- 중복 제거.
- 필드는 `id`, `name`, `abbreviation`.
- 정렬은 내부 플랫폼 그룹 우선순위 후 이름순.

내부 정렬 우선순위:

1. PC
2. PlayStation
3. Nintendo
4. Xbox
5. Mobile
6. 그룹 미정

`genres`:

- `service.genre` 기준.
- `id`, `name`.
- 이름순 정렬.
- 없으면 `[]`.

`developers`:

- `service.company` + `service.game_company.is_developer = true`.
- `id`, `name`.
- 이름순 정렬.
- 없으면 `[]`.
- publisher, porting, supporting 회사는 제외한다.

`koreanLanguageSupport`:

- 한국어 지원 정보만 포함.
- 서버 내부 매핑으로 한국어 language를 식별한다.
- `locale`, `name`, `native_name`, 필요 시 `id` 기준.
- 없으면 `null`.

```json
{
  "audio": false,
  "subtitles": true,
  "interface": true
}
```

`websites`:

- trusted 링크만 포함.
- `service.website.is_trusted = true`.
- `id`, `url`, `type`.
- `isTrusted`는 응답하지 않는다.
- URL순 정렬.
- 없으면 `[]`.

```json
{
  "id": 10,
  "url": "https://example.com",
  "type": {
    "id": 1,
    "type": "official"
  }
}
```

`type`이 없으면 `null`.

`video`:

- 대표 영상 1개.
- 없으면 `null`.
- `service.game_video.video_id` 기반.
- DB에는 URL이 없으므로 서버가 YouTube URL과 썸네일 URL을 생성한다.
- MVP에서는 YouTube로 가정한다.
- `videoId`가 없으면 대표 영상 후보에서 제외한다.

대표 영상 선택:

1. 이름에 `trailer`, `official`, `gameplay` 등 대표성 높은 키워드가 있는 영상 우선.
2. 여러 개면 안정 정렬 기준으로 1개.
3. 없으면 ID순 첫 번째 fallback.

필드:

- `id`
- `name`
- `videoId`
- `url`
- `thumbnailUrl`

## 로컬라이제이션 정책

제목:

- 한국 로컬라이제이션 제목 우선.
- 없으면 기본 제목.
- 표시 제목을 만들 수 없으면 릴리즈 목록에서는 제외, 게임 상세는 404.

커버:

- 한국 로컬라이제이션 커버 우선.
- 없으면 `is_main = true` 메인 커버.
- 없으면 아무 커버.
- 없으면 `null`.

언어:

- 한국어 지원 정보만 API에 노출한다.
- 전체 언어 목록은 현 단계에서 제공하지 않는다.

## 현재 service 스키마상 가능한 데이터

현재 스키마에서 직접 조회 가능한 주요 테이블:

- `service.game`
- `service.game_release`
- `service.release_region`
- `service.release_status`
- `service.platform`
- `service.game_type`
- `service.game_status`
- `service.game_localization`
- `service.cover`
- `service.game_language`
- `service.language`
- `service.genre`
- `service.game_genre`
- `service.company`
- `service.game_company`
- `service.website`
- `service.website_type`
- `service.game_video`

현재 스키마만으로 명확하지 않아 현 단계 제외 또는 내부 파생 처리하는 것:

- 선행 플레이
- 오픈 베타
- 데모
- 성인/민감 콘텐츠
- 명시적 주요 출시작 플래그
- platform group 응답 필드
- region code 응답 필드

## 구현 시 주의점

- `calendar` 모듈은 `batch` 모듈에 의존하면 안 된다.
- 기존 architecture test에서 `calendar` -> `batch` 의존을 금지한다.
- 따라서 `batch.model.entity.service` 엔티티를 재사용하지 말고, `calendar` 쪽 조회 DTO/Repository를 별도로 둔다.
- `serviceJdbcTemplate`을 사용하는 조회 repository를 두는 방향이 자연스럽다.
- 릴리즈 API는 `service.game_release`를 중심으로 조인한다.
- 게임 상세 API는 `service.game`을 중심으로 필요한 정보만 별도 조회/조합한다.
- 참조 누락은 가능한 한 warning log로 남기고 사용자 응답은 유지한다.

## 구현 전 최종 확인 필요

구현 단계에서 아래는 실제 DB 값 또는 테스트 데이터로 확인해야 한다.

- `service.release_region`의 한국/글로벌 id/name 값.
- `service.platform`의 주요 플랫폼 id/name/abbreviation 값.
- `service.language`의 한국어 locale/name/native_name 값.
- `service.cover.url`과 `image_id` 기반 IGDB 이미지 URL 생성 규칙.
- `service.game_video.video_id`가 실제 YouTube id인지 여부.
- `service.release_status.name` 값의 실제 분포.
- `service.game_type.type` 값의 실제 분포.

