-- After approved baseline only. Read-only metadata, not business rows/passwords.
SELECT json_build_object(
    'identity',json_build_object('database',current_database(),'address',host(inet_server_addr()),
        'port',inet_server_port(),'serverVersion',current_setting('server_version_num')::integer,
        'readOnly',current_setting('transaction_read_only')),
    'history',(SELECT json_agg(json_build_object('rank',installed_rank,'version',version,
        'description',description,'type',type,'script',script,'checksum',checksum,
        'installedBy',installed_by,'installedOn',installed_on,'executionTime',execution_time,'success',success)
        ORDER BY installed_rank) FROM public.flyway_schema_history),
    'columns',(SELECT json_agg(json_build_object('name',a.attname,
        'type',format_type(a.atttypid,a.atttypmod),'notNull',a.attnotnull,
        'default',pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum)
        FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
        WHERE a.attrelid='public.flyway_schema_history'::regclass AND a.attnum>0 AND NOT a.attisdropped),
    'indexes',(SELECT json_agg(json_build_object('name',c.relname,'definition',pg_get_indexdef(c.oid),
        'valid',i.indisvalid,'ready',i.indisready) ORDER BY c.relname)
        FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
        WHERE i.indrelid='public.flyway_schema_history'::regclass),
    'applicationCanWriteHistory',has_table_privilege('campus_plus_user',
        'public.flyway_schema_history','INSERT,UPDATE,DELETE,TRUNCATE')
);
