-- Read-only before/after ownership and ACL evidence; no row or credential exports.
SELECT json_build_object(
    'identity',json_build_object('database',current_database(),'address',host(inet_server_addr()),
        'port',inet_server_port(),'serverVersion',current_setting('server_version_num')::integer,
        'readOnly',current_setting('transaction_read_only')),
    'database',(SELECT json_build_object('owner',pg_get_userbyid(datdba),'acl',datacl::text)
        FROM pg_database WHERE datname=current_database()),
    'publicSchema',(SELECT json_build_object('owner',pg_get_userbyid(nspowner),'acl',nspacl::text)
        FROM pg_namespace WHERE nspname='public'),
    'relations',(SELECT COALESCE(json_agg(json_build_object('name',c.relname,'kind',c.relkind,
        'owner',pg_get_userbyid(c.relowner),'acl',c.relacl::text) ORDER BY c.relname),'[]'::json)
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public')
);
