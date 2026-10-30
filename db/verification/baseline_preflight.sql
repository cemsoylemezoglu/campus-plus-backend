-- Read-only metadata adoption preflight; contains no business rows or password values.
SELECT json_build_object(
    'identity', json_build_object('database',current_database(),'address',host(inet_server_addr()),
        'port',inet_server_port(),'serverVersion',current_setting('server_version_num')::integer,
        'readOnly',current_setting('transaction_read_only')),
    'migrationHistoryPresent',to_regclass('public.flyway_schema_history') IS NOT NULL,
    'enabledEventTriggers',(SELECT COALESCE(json_agg(evtname ORDER BY evtname),'[]'::json)
        FROM pg_event_trigger WHERE evtenabled<>'D'),
    'cemDefaultTableGrants',(SELECT COALESCE(json_agg(json_build_object(
        'schema',CASE WHEN d.defaclnamespace=0 THEN 'GLOBAL' ELSE n.nspname END,
        'grantee',CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END,
        'privilege',a.privilege_type,'grantable',a.is_grantable)
        ORDER BY d.defaclnamespace,a.grantee,a.privilege_type),'[]'::json)
        FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
        CROSS JOIN LATERAL aclexplode(d.defaclacl) a
        WHERE d.defaclrole=(SELECT oid FROM pg_roles WHERE rolname='cem') AND d.defaclobjtype='r'
        AND (d.defaclnamespace=0 OR n.nspname='public'))
);
