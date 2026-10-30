-- Read-only preservation evidence. Returns aggregate digests, NEVER individual user/hash values.
SELECT jsonb_build_object(
    'identity', jsonb_build_object('database',current_database(),'address',host(inet_server_addr()),
        'port',inet_server_port(),'serverVersion',current_setting('server_version_num')::integer,
        'readOnly',current_setting('transaction_read_only')),
    'fingerprints', jsonb_build_object(
        'users', (SELECT jsonb_build_object('count',count(*),'digest',md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text)) FROM public.users t),
        'user_roles', (SELECT jsonb_build_object('count',count(*),'digest',md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY user_id,role),'[]'::jsonb)::text)) FROM public.user_roles t),
        'universities', (SELECT jsonb_build_object('count',count(*),'digest',md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text)) FROM public.universities t),
        'student_profiles', (SELECT jsonb_build_object('count',count(*),'digest',md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY user_id),'[]'::jsonb)::text)) FROM public.student_profiles t),
        'clubs', (SELECT jsonb_build_object('count',count(*),'digest',md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text)) FROM public.clubs t),
        'club_memberships', (SELECT jsonb_build_object('count',count(*),'digest',md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text)) FROM public.club_memberships t)
    )
);
