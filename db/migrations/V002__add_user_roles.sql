-- Approved Campus+ RBAC migration. Explicit execution only; never auto-run.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15s';
DO $$
DECLARE
    original_users jsonb;
BEGIN
    IF current_database() <> 'campus_plus'
        OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
        OR inet_server_port() IS DISTINCT FROM 5434
        OR current_setting('server_version_num')::integer NOT BETWEEN 170000 AND 179999 THEN
        RAISE EXCEPTION 'Not the authorized Campus+ PostgreSQL 17 target';
    END IF;
    LOCK TABLE public.users IN SHARE ROW EXCLUSIVE MODE;
    IF to_regclass('public.user_roles') IS NOT NULL THEN
        RAISE EXCEPTION 'Unexpected existing user_roles table; inspect before proceeding';
    END IF;
    SELECT coalesce(jsonb_agg(to_jsonb(u) ORDER BY id), '[]'::jsonb)
        INTO original_users FROM public.users u;

    CREATE TABLE public.user_roles (
        user_id BIGINT NOT NULL REFERENCES public.users(id),
        role VARCHAR(20) NOT NULL,
        CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
        CONSTRAINT ck_user_roles_role CHECK (role IN ('STUDENT', 'CLUB_ADMIN', 'ADMIN'))
    );
    INSERT INTO public.user_roles (user_id, role) SELECT id, 'STUDENT' FROM public.users;

    IF original_users IS DISTINCT FROM
        (SELECT coalesce(jsonb_agg(to_jsonb(u) ORDER BY id), '[]'::jsonb) FROM public.users u)
        OR (SELECT count(*) FROM public.user_roles) <> (SELECT count(*) FROM public.users)
        OR EXISTS (SELECT 1 FROM public.user_roles WHERE role <> 'STUDENT') THEN
        RAISE EXCEPTION 'User preservation or default-role verification failed';
    END IF;
END $$;
COMMIT;
