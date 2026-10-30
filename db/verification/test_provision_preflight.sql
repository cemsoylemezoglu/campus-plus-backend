-- Read-only: inspect only Campus+ names and PUBLIC access on the source schema.
SELECT json_build_object(
    'identity', json_build_object('database', current_database(),
        'address', host(inet_server_addr()), 'port', inet_server_port(),
        'serverVersion', current_setting('server_version_num')::integer,
        'readOnly', current_setting('transaction_read_only')),
    'testDatabaseExists', EXISTS (SELECT FROM pg_database WHERE datname='campus_plus_test'),
    'testRolesExist', EXISTS (SELECT FROM pg_roles WHERE rolname IN
        ('campus_plus_test_migrator', 'campus_plus_test_runner')),
    'operatorIsSuperuser', (SELECT rolsuper FROM pg_roles WHERE rolname=current_user),
    'operatorCanCreateRoles', (SELECT rolcreaterole FROM pg_roles WHERE rolname=current_user),
    'operatorCanCreateDatabases', (SELECT rolcreatedb FROM pg_roles WHERE rolname=current_user),
    'sourcePublicTablePrivileges', (SELECT COALESCE(json_agg(DISTINCT a.privilege_type), '[]'::json)
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        CROSS JOIN LATERAL aclexplode(COALESCE(c.relacl, acldefault('r', c.relowner))) a
        WHERE n.nspname='public' AND c.relkind IN ('r','p') AND a.grantee=0),
    'sourcePublicColumnPrivileges', (SELECT COALESCE(json_agg(DISTINCT a.privilege_type), '[]'::json)
        FROM pg_attribute col JOIN pg_class c ON c.oid=col.attrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace
        CROSS JOIN LATERAL aclexplode(col.attacl) a
        WHERE n.nspname='public' AND col.attnum>0 AND NOT col.attisdropped AND a.grantee=0),
    'sourcePublicSequencePrivileges', (SELECT COALESCE(json_agg(DISTINCT a.privilege_type), '[]'::json)
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        CROSS JOIN LATERAL aclexplode(COALESCE(c.relacl, acldefault('S', c.relowner))) a
        WHERE n.nspname='public' AND c.relkind='S' AND a.grantee=0),
    'sourcePublicCanCreateInPublic', EXISTS (SELECT FROM pg_namespace n
        CROSS JOIN LATERAL aclexplode(COALESCE(n.nspacl, acldefault('n', n.nspowner))) a
        WHERE n.nspname='public' AND a.grantee=0 AND a.privilege_type='CREATE'),
    'sourcePublicCanCreateSchema', EXISTS (SELECT FROM pg_database d
        CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl, acldefault('d', d.datdba))) a
        WHERE d.datname=current_database() AND a.grantee=0 AND a.privilege_type='CREATE')
);
