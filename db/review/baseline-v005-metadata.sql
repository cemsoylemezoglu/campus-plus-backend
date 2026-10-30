-- REVIEW ONLY: expected Flyway 12.4.0 baseline metadata in public.
-- Do NOT execute this file. Use the separately approved Flyway baseline command.
-- Intentional failure + transaction rollback prevent an accidental whole-file apply.
BEGIN;
DO $$ BEGIN RAISE EXCEPTION 'Review-only SQL; use approved Flyway baseline, never hand-write history'; END $$;

CREATE TABLE public.flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT now(),
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL
);
ALTER TABLE public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history (success);
INSERT INTO public.flyway_schema_history
    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (1, '5', 'Campus+ reviewed V005 schema', 'BASELINE',
        'Campus+ reviewed V005 schema', NULL, current_user, 0, TRUE);

ROLLBACK;
