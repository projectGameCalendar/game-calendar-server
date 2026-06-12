-- service ETL이 diff 기반 전량 비교 방식이라 커서 메커니즘이 사용되지 않음 (애플리케이션 코드 제거 완료)
-- V1.0.2.6에서 생성된 잔존 테이블 정리
DROP TABLE IF EXISTS service.etl_cursor;
