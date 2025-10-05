-- ==========================================
-- V1__suggestions_notify_trigger.sql
-- 목적:
--   - suggestions 테이블에 INSERT 발생 시
--     Postgres NOTIFY 이벤트를 발행하여
--     SSE(Server-Sent Events)로 전달 가능하게 함
-- ==========================================

-- 1) NOTIFY 함수 정의
CREATE OR REPLACE FUNCTION notify_suggestions_insert() RETURNS trigger AS $$
DECLARE
    payload JSON;
BEGIN
    payload := json_build_object(
            'event',  'insert',
            'table',  TG_TABLE_NAME,
            'id',     NEW.id,
            'userId', NEW.user_id,
            'ts',     EXTRACT(EPOCH FROM clock_timestamp())::bigint
               );
    PERFORM pg_notify('suggestions_channel', payload::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2) 트리거 생성
DROP TRIGGER IF EXISTS trg_suggestions_notify_insert ON suggestions;
CREATE TRIGGER trg_suggestions_notify_insert
    AFTER INSERT ON suggestions
    FOR EACH ROW
EXECUTE FUNCTION notify_suggestions_insert();
