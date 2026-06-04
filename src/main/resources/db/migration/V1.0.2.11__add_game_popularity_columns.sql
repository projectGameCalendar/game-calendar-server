-- IGDB game endpoint의 인기도 시그널 컬럼 추가
--   - hypes:   출시 전 기대도 (IGDB 사용자가 미출시 게임에 wanted 등록한 누적 수)
--   - follows: 팔로워 수 (게임을 follow한 IGDB 사용자 총 수)
-- IGDB는 정수 카운트로 노출하며 null/0 모두 가능. 표본은 igdb.com 회원 활동 기반.
-- 캘린더 노이즈 필터/정렬 키로 활용 예정.

ALTER TABLE ingest.game
    ADD COLUMN IF NOT EXISTS hypes   INTEGER,
    ADD COLUMN IF NOT EXISTS follows INTEGER;

ALTER TABLE service.game
    ADD COLUMN IF NOT EXISTS hypes   INTEGER,
    ADD COLUMN IF NOT EXISTS follows INTEGER;
