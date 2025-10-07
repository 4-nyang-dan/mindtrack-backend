package com.example.mindtrack.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class SuggestionsTriggerEnsurer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        jdbc.execute("""
      DO $$
      BEGIN
        IF NOT EXISTS (
          SELECT 1 FROM pg_trigger WHERE tgname='trg_suggestions_notify_insert'
        ) THEN
          CREATE OR REPLACE FUNCTION notify_suggestions_insert() RETURNS trigger AS $BODY$
          DECLARE
            payload JSON;
          BEGIN
            payload := json_build_object(
              'event','insert',
              'table', TG_TABLE_NAME,
              'id', NEW.id,
              'userId', NEW.user_id,
              'ts', EXTRACT(EPOCH FROM clock_timestamp())::bigint
            );
            PERFORM pg_notify('suggestions_channel', payload::text);
            RETURN NEW;
          END;
          $BODY$ LANGUAGE plpgsql;

          CREATE TRIGGER trg_suggestions_notify_insert
            AFTER INSERT ON public.suggestions
            FOR EACH ROW
            EXECUTE FUNCTION notify_suggestions_insert();
        END IF;
      END $$;
    """);
    }
}
