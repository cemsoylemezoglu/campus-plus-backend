-- Campus+ only. Execute with psql -X -v ON_ERROR_STOP=1 against 127.0.0.1:5434/campus_plus.
-- Deliberately not auto-run; reapplying an already applied migration is an error.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15s';

DO $$
DECLARE
    original_rows jsonb;
    remaining_rows jsonb;
BEGIN
    IF current_database() <> 'campus_plus'
        OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
        OR inet_server_port() IS DISTINCT FROM 5434
        OR current_setting('server_version_num')::integer NOT BETWEEN 170000 AND 179999 THEN
        RAISE EXCEPTION 'Migration target is not the authorized Campus+ PostgreSQL 17 database';
    END IF;

    LOCK TABLE public.users IN ACCESS EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'password_hash') THEN
        RAISE EXCEPTION 'Unexpected schema: password_hash already exists; inspect before proceeding';
    END IF;

    SELECT coalesce(jsonb_agg(to_jsonb(u) ORDER BY u.id), '[]'::jsonb)
        INTO original_rows FROM public.users u;

    ALTER TABLE public.users ADD COLUMN password_hash VARCHAR(255);

    SELECT coalesce(jsonb_agg(to_jsonb(u) - 'password_hash' ORDER BY u.id), '[]'::jsonb)
        INTO remaining_rows FROM public.users u;
    IF original_rows IS DISTINCT FROM remaining_rows
        OR EXISTS (SELECT 1 FROM public.users WHERE password_hash IS NOT NULL) THEN
        RAISE EXCEPTION 'Existing user records were not preserved';
    END IF;
END $$;
COMMIT;
