-- ==========================================
-- V1__init_schema.sql
-- 목적:
--   - suggestions / suggestion_items 테이블 생성
--   - suggestions.image_id 로 screenshot_image 연결 (느슨 FK)
--   - Postgres NOTIFY 트리거 구성 (INSERT시)
-- ==========================================

-- 0) 확장: UUID 생성용
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1) 상위 테이블: suggestions
CREATE TABLE IF NOT EXISTS suggestions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     TEXT NOT NULL,
    image_id    BIGINT,  -- 원본 스크린이미지 id 연결
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2) 하위 테이블: suggestion_items
CREATE TABLE IF NOT EXISTS suggestion_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id  BIGINT NOT NULL REFERENCES suggestions(id) ON DELETE CASCADE,
    question       TEXT NOT NULL,
    answer         TEXT,                -- NULL 허용
    confidence     DOUBLE PRECISION,
    rank           INT                  -- TopN 순서(1..N)
);

-- 3) FK 연결: suggestions.image_id -> screenshot_image.id
-- (screenshot_image 테이블이 있을 경우에만 추가)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'screenshot_image') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'fk_suggestions_image'
        ) THEN
            ALTER TABLE suggestions
                ADD CONSTRAINT fk_suggestions_image
                FOREIGN KEY (image_id)
                REFERENCES screenshot_image(id)
                ON DELETE SET NULL;
        END IF;
    END IF;
END$$;

-- 4) 인덱스 / 유니크 제약
CREATE INDEX IF NOT EXISTS idx_suggestions_user_created
    ON suggestions(user_id, created_at DESC);

-- (user_id + image_id) 중복 방지
CREATE UNIQUE INDEX IF NOT EXISTS ux_suggestions_user_image
    ON suggestions(user_id, image_id);

-- 5) NOTIFY 트리거 (INSERT ON suggestions)
-- 함수 정의
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

-- 트리거 생성
DROP TRIGGER IF EXISTS trg_suggestions_notify_insert ON suggestions;
CREATE TRIGGER trg_suggestions_notify_insert
    AFTER INSERT ON suggestions
    FOR EACH ROW
EXECUTE FUNCTION notify_suggestions_insert();
