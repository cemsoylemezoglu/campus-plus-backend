-- Read-only catalogue inventory. No user rows, passwords, sequence values or ownership exports.
SELECT jsonb_build_object(
    'migrationHistoryPresent', to_regclass('public.flyway_schema_history') IS NOT NULL,
    'databaseEnvironment', (SELECT jsonb_build_object(
        'encoding', pg_encoding_to_char(d.encoding), 'collate', d.datcollate,
        'ctype', d.datctype, 'localeProvider', d.datlocprovider)
        FROM pg_database d WHERE d.datname=current_database()),
    'identity', jsonb_build_object(
        'database', current_database(), 'address', host(inet_server_addr()),
        'port', inet_server_port(), 'serverVersion', current_setting('server_version_num')::integer,
        'readOnly', current_setting('transaction_read_only')),
    'schema', jsonb_build_object(
        'tables', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'name', c.relname, 'kind', c.relkind, 'rls', c.relrowsecurity,
                'forceRls', c.relforcerowsecurity,
                'columns', (SELECT jsonb_agg(jsonb_build_object(
                    'name', a.attname, 'type', format_type(a.atttypid, a.atttypmod),
                    'notNull', a.attnotnull, 'identity', a.attidentity, 'generated', a.attgenerated,
                    'collation', (SELECT cn.nspname||'.'||cl.collname FROM pg_collation cl
                        JOIN pg_namespace cn ON cn.oid=cl.collnamespace WHERE cl.oid=a.attcollation),
                    'default', pg_get_expr(d.adbin, d.adrelid)) ORDER BY a.attnum)
                    FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                    WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),
                'constraints', coalesce((SELECT jsonb_agg(jsonb_build_object(
                    'name', k.conname, 'kind', k.contype, 'definition', pg_get_constraintdef(k.oid),
                    'validated', k.convalidated, 'deferrable', k.condeferrable,
                    'deferred', k.condeferred) ORDER BY k.conname)
                    FROM pg_constraint k WHERE k.conrelid=c.oid), '[]'::jsonb),
                'indexes', coalesce((SELECT jsonb_agg(jsonb_build_object(
                    'name', i.relname, 'definition', pg_get_indexdef(i.oid),
                    'unique', x.indisunique, 'primary', x.indisprimary,
                    'valid', x.indisvalid, 'ready', x.indisready) ORDER BY i.relname)
                    FROM pg_index x JOIN pg_class i ON i.oid=x.indexrelid
                    WHERE x.indrelid=c.oid), '[]'::jsonb)
            ) ORDER BY c.relname)
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind IN ('r','p')
                AND c.relname<>'flyway_schema_history'
        ), '[]'::jsonb),
        'sequences', coalesce((SELECT jsonb_agg(jsonb_build_object(
            'name', s.relname, 'type', format_type(q.seqtypid,NULL),
            'start', q.seqstart, 'increment', q.seqincrement, 'min', q.seqmin,
            'max', q.seqmax, 'cache', q.seqcache, 'cycle', q.seqcycle,
            'table', t.relname, 'column', a.attname, 'dependency', d.deptype
        ) ORDER BY s.relname)
            FROM pg_class s JOIN pg_namespace n ON n.oid=s.relnamespace
            JOIN pg_sequence q ON q.seqrelid=s.oid
            LEFT JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=s.oid
                AND d.refclassid='pg_class'::regclass AND d.deptype IN ('a','i')
            LEFT JOIN pg_class t ON t.oid=d.refobjid
            LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=d.refobjsubid
            WHERE n.nspname='public' AND s.relkind='S'), '[]'::jsonb),
        'otherRelations', coalesce((SELECT jsonb_agg(jsonb_build_object('name',c.relname,'kind',c.relkind) ORDER BY c.relname)
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind NOT IN ('r','p','S','i','I')), '[]'::jsonb),
        'triggers', coalesce((SELECT jsonb_agg(jsonb_build_object(
            'table',c.relname,'name',t.tgname,'definition',pg_get_triggerdef(t.oid)) ORDER BY c.relname,t.tgname)
            FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND NOT t.tgisinternal), '[]'::jsonb),
        'routines', coalesce((SELECT jsonb_agg(jsonb_build_object(
            'name',p.proname,'arguments',pg_get_function_identity_arguments(p.oid),'kind',p.prokind)
            ORDER BY p.proname,pg_get_function_identity_arguments(p.oid))
            FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'), '[]'::jsonb),
        'extensions', coalesce((SELECT jsonb_agg(e.extname ORDER BY e.extname) FROM pg_extension e), '[]'::jsonb)
    )
);
