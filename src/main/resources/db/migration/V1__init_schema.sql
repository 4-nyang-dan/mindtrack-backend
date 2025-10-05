-- ==========================================
-- V1__suggestions_notify_trigger.sql
-- 목적:
--   - suggestions 테이블에 INSERT 발생 시
--     Postgres NOTIFY 이벤트를 발행하여
--     SSE(Server-Sent Events)로 전달 가능하게 함
-- ==========================================



--** Flyway 가 항상 먼저 실행되므로 이를 해결하기 위해 조건문 추가 : suggestions 테이블이 존재할 때만 트리거를 생성suggestions 테이블이 존재할 때만 트리거를 생성
-- DO $$ 블록을 사용해서 내부적으로 “트랜잭션 + 함수 실행”처럼 동작
-- 기존 코드 문제점: 순수 SQL 문장이기 때문에 Flyway가 순서대로 실행 -> 따라서 Flyway 가 JPA 보다 먼저 실행돼서 
-- suggestion 테이블이 생성되지 못한 채 트리거를 수행하려고 해서 에러 발생. 
DO $$
BEGIN
    -- 테이블 있는지 먼저 검사 
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'suggestions') THEN

        -- 1) 함수 정의
        EXECUTE '
            CREATE OR REPLACE FUNCTION notify_suggestions_insert() RETURNS trigger AS $BODY$
            DECLARE
                payload JSON;
            BEGIN
                payload := json_build_object(
                    ''event'',  ''insert'',
                    ''table'',  TG_TABLE_NAME,
                    ''id'',     NEW.id,
                    ''userId'', NEW.user_id,
                    ''ts'',     EXTRACT(EPOCH FROM clock_timestamp())::bigint
                );
                PERFORM pg_notify(''suggestions_channel'', payload::text);
                RETURN NEW;
            END;
            $BODY$ LANGUAGE plpgsql;
        ';

        -- 2) 트리거 생성
        EXECUTE 'DROP TRIGGER IF EXISTS trg_suggestions_notify_insert ON suggestions';
        EXECUTE 'CREATE TRIGGER trg_suggestions_notify_insert
                 AFTER INSERT ON suggestions
                 FOR EACH ROW
                 EXECUTE FUNCTION notify_suggestions_insert()';

    ELSE
        RAISE NOTICE 'Table "suggestions" not found. Skipping trigger creation.';
    END IF;
END $$;