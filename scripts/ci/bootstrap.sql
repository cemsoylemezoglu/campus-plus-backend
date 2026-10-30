-- CI ONLY: fresh, private initdb cluster, verified by scripts/ci_postgres.py.
-- Not an alternative local provisioning script. Never run on the user's PG17 server.
DO $$ BEGIN
    IF current_database()<>'postgres' OR current_user<>'campus_ci_owner'
       OR host(inet_server_addr()) IS DISTINCT FROM '127.0.0.1'
       OR inet_server_port() IS DISTINCT FROM 5434
       OR current_setting('server_version_num')::int NOT BETWEEN 170000 AND 179999 THEN
        RAISE EXCEPTION 'Unauthorized CI bootstrap target';
    END IF;
END $$;
BEGIN;
CREATE ROLE campus_plus_test_migrator NOLOGIN PASSWORD NULL
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
CREATE ROLE campus_plus_test_runner NOLOGIN PASSWORD NULL
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
COMMIT;
CREATE DATABASE campus_plus_test OWNER campus_plus_test_migrator TEMPLATE template0
    ENCODING 'UTF8' LOCALE_PROVIDER libc LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8';
REVOKE ALL ON DATABASE campus_plus_test FROM PUBLIC;
GRANT CONNECT ON DATABASE campus_plus_test TO campus_plus_test_runner;
