-- 확장 설치 (UUID 생성용)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 상위 테이블: SuggestionPayload
DROP TABLE IF EXISTS suggestion_items CASCADE;
DROP TABLE IF EXISTS suggestions CASCADE;

CREATE TABLE IF NOT EXISTS suggestions (
   id          BIGSERIAL PRIMARY KEY,
   user_id     TEXT NOT NULL,
   created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 하위 테이블: Suggestion (Top3)
CREATE TABLE IF NOT EXISTS suggestion_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id  BIGINT NOT NULL REFERENCES suggestions(id) ON DELETE CASCADE,
    question       TEXT NOT NULL,
    answer         TEXT NOT NULL,
    confidence     DOUBLE PRECISION
);

-- 트리거 함수: SuggestionPayload insert 시 pg_notify
CREATE OR REPLACE FUNCTION notify_suggestions_insert() RETURNS trigger AS $$
DECLARE
    payload JSON;
BEGIN
    payload := json_build_object(
            'event', 'insert',
            'table', TG_TABLE_NAME,
            'id', NEW.id,
            'userId', NEW.user_id,
            'ts', EXTRACT(EPOCH FROM clock_timestamp())::bigint
               );
    PERFORM pg_notify('suggestions_channel', payload::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 트리거 연결
DROP TRIGGER IF EXISTS trg_suggestions_notify_insert ON suggestions;

CREATE TRIGGER trg_suggestions_notify_insert
    AFTER INSERT ON suggestions
    FOR EACH ROW
EXECUTE FUNCTION notify_suggestions_insert();
