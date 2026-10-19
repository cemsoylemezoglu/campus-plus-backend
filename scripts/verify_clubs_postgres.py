#!/usr/bin/env python3
"""Opt-in live Campus+ tests. Creates and retains labelled fixtures; never uses Docker.

Requires CAMPUS_PLUS_LIVE_TESTS=1, DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET,
PostgreSQL 17 psql, and the packaged application. Credentials stay in process memory.
Only 127.0.0.1:5434/campus_plus is accepted. Existing rows are fingerprinted and checked.
New test ADMIN is always revoked; successful runs archive/deactivate test fixtures.
On an unexpected result, stop testing, revoke test ADMIN and stop only our app process.
"""

import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import queue
import secrets
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse
import uuid


class LiveChecks:
    def __init__(self):
        assert os.environ.get('CAMPUS_PLUS_LIVE_TESTS') == '1', 'Explicit live-test opt-in required'
        url = urlparse(os.environ['DB_URL'].removeprefix('jdbc:'))
        assert (url.scheme, url.hostname, url.port, url.path, url.query) == (
            'postgresql', '127.0.0.1', 5434, '/campus_plus', ''), 'Unauthorized DB target'
        assert len(base64.b64decode(os.environ['JWT_SECRET'], validate=True)) >= 32
        self.tag = uuid.uuid4().hex[:10]
        self.app_name = 'campusplus_club_check_' + self.tag
        self.psql = [os.environ.get('CAMPUS_PLUS_PSQL', '/opt/homebrew/opt/postgresql@17/bin/psql'),
                     '-X', '-w', '-q', '-A', '-t', '-h', '127.0.0.1', '-p', '5434',
                     '-U', os.environ['DB_USERNAME'], '-d', 'campus_plus', '-v', 'ON_ERROR_STOP=1']
        self.env = os.environ.copy()
        self.env.update(PGPASSWORD=os.environ['DB_PASSWORD'], PGCONNECT_TIMEOUT='5')
        self.env.pop('PGOPTIONS', None)
        self.checks = []
        self.users = {}
        self.clubs = []
        self.universities = []
        self.admin_granted = False
        self.app = None
        self.pool = ThreadPoolExecutor(max_workers=4)
        self.log_path = Path('/tmp/campus-plus-clubs-postgres-app.log')
        self.result_path = Path('/tmp/campus-plus-clubs-postgres-result.json')
        self.phase = 'identity'

    def sql(self, statement, write=False, expected_state=None):
        env = self.env.copy()
        if not write:
            env['PGOPTIONS'] = '-c default_transaction_read_only=on'
        result = subprocess.run(self.psql, input='\\set VERBOSITY sqlstate\n' + statement,
                                env=env, text=True, capture_output=True, timeout=20)
        if expected_state:
            assert result.returncode != 0 and expected_state in result.stderr, 'Expected PostgreSQL constraint was not enforced'
            return ''
        assert result.returncode == 0, 'PostgreSQL command failed (credential/row details withheld)'
        return result.stdout.strip()

    def snapshots(self):
        result = {}
        for table, order in [('users', 'id'), ('user_roles', 'user_id,role'),
                             ('universities', 'id'), ('student_profiles', 'user_id')]:
            rows = json.loads(self.sql('SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY ' + order
                                     + "),'[]'::jsonb) FROM public." + table + ' t;'))
            result[table] = {
                str((r['user_id'], r['role']) if table == 'user_roles' else r.get('id', r.get('user_id'))):
                hashlib.sha256(json.dumps(r, sort_keys=True).encode()).hexdigest() for r in rows}
        return result

    def record(self, label):
        self.checks.append(label)
        print('PASS: ' + label, flush=True)

    def http(self, method, path, data=None, actor=None):
        headers = {}
        payload = None
        if data is not None:
            headers['Content-Type'] = 'application/json'
            payload = json.dumps(data).encode()
        if actor:
            headers['Authorization'] = 'Bearer ' + self.users[actor]['token']
        request = urllib.request.Request(self.base + path, data=payload, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.loads(response.read()), dict(response.headers)
        except urllib.error.HTTPError as error:
            body = error.read()
            try:
                decoded = json.loads(body)
            except ValueError:
                decoded = {}
            return error.code, decoded, dict(error.headers)

    def expect(self, label, method, path, data=None, actor=None, status=200):
        actual, body, headers = self.http(method, path, data, actor)
        assert actual == status, label + ': HTTP ' + str(actual) + ', expected ' + str(status)
        self.record(label)
        return body

    def parallel(self, label, calls, expected):
        barrier = threading.Barrier(len(calls))
        def call(args):
            barrier.wait(timeout=5)
            return self.http(*args)
        futures = [self.pool.submit(call, args) for args in calls]
        responses = [future.result(timeout=25) for future in futures]
        assert sorted(r[0] for r in responses) == sorted(expected), label + ': unexpected parallel HTTP results'
        self.record(label)
        return responses

    def club(self, cid, actor='admin'):
        status, body, _ = self.http('GET', '/api/clubs/' + str(cid), actor=actor)
        assert status == 200, 'Club read failed'
        return body

    def member(self, cid, actor):
        status, body, _ = self.http('GET', '/api/me/club-memberships?size=100', actor=actor)
        assert status == 200, 'Own membership read failed'
        return next(m for m in body['content'] if m['club']['id'] == cid)

    def start(self):
        identity = self.sql("SELECT current_database(),inet_server_addr(),inet_server_port(),current_setting('server_version_num');").split('|')
        assert identity[:3] == ['campus_plus', '127.0.0.1', '5434'] and 170000 <= int(identity[3]) < 180000
        self.before = self.snapshots()
        self.record('Authorized Campus+ PostgreSQL 17 identity')
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        self.base = 'http://127.0.0.1:' + str(port)
        env = os.environ.copy()
        env.update(DB_URL='jdbc:postgresql://127.0.0.1:5434/campus_plus?ApplicationName=' + self.app_name,
                   SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(port), SPRING_JPA_SHOW_SQL='false',
                   LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB='WARN',
                   LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY='WARN',
                   LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND='OFF',
                   LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BOOT_SECURITY_AUTOCONFIGURE='OFF')
        fd = os.open(self.log_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        os.fchmod(fd, 0o600)
        self.log = os.fdopen(fd, 'w')
        self.app = subprocess.Popen(['java', '-jar', 'target/campus-plus-backend-0.0.1-SNAPSHOT.jar'],
                                    env=env, stdout=self.log, stderr=subprocess.STDOUT)
        for _ in range(40):
            assert self.app.poll() is None, 'Test application startup failed; stop and inspect protected log'
            try:
                status, _, _ = self.http('GET', '/api/clubs')
                assert status == 401, 'Unexpected anonymous access'
                self.record('Live application/schema validation and anonymous 401')
                return
            except urllib.error.URLError:
                time.sleep(0.5)
        raise AssertionError('Test application startup timed out')

    def fixtures(self):
        self.phase = 'new test fixtures'
        for actor in ['admin', 'owner', 'other', 'first', 'second', 'outsider', 'racer']:
            email = 'campusplus-clubs-' + self.tag + '-' + actor + '@example.com'
            password = secrets.token_urlsafe(24)
            user = self.expect('Register separate ' + actor, 'POST', '/api/auth/register',
                               {'name': 'Club Check ' + actor, 'email': email, 'password': password}, status=201)
            assert str(user['id']) not in self.before['users'], 'Existing user collision'
            login = self.expect('BCrypt login ' + actor, 'POST', '/api/auth/login', {'email': email, 'password': password})
            self.users[actor] = {'id': user['id'], 'token': login['accessToken'], 'email': email}
        admin_id = self.users['admin']['id']
        self.admin_granted = True
        self.sql("INSERT INTO public.user_roles(user_id,role) SELECT id,'ADMIN' FROM public.users WHERE id="
                 + str(admin_id) + " AND email='" + self.users['admin']['email'] + "';", write=True)
        self.record('Temporary ADMIN granted only to newly registered fixture')
        for suffix in ['A', 'B']:
            uni = self.expect('Create separate university ' + suffix, 'POST', '/api/universities',
                              {'code': 'CLUB_' + self.tag.upper() + '_' + suffix, 'name': 'Club Check ' + self.tag + ' ' + suffix,
                               'city': 'Istanbul', 'countryCode': 'TR'}, 'admin', 201)
            self.universities.append(uni['id'])
        for actor in ['owner', 'other', 'first', 'second', 'outsider', 'racer']:
            uni = self.universities[1 if actor == 'outsider' else 0]
            self.expect('Create own profile ' + actor, 'POST', '/api/me/student-profile',
                        {'universityId': uni, 'department': 'Software Engineering', 'studyYear': 2}, actor, 201)

    def create_club(self, suffix, actor):
        response = self.expect('Create pending club ' + suffix, 'POST', '/api/clubs',
                               {'name': 'Live Club ' + suffix, 'slug': 'live-' + self.tag + '-' + suffix.lower(),
                                'description': 'Separate live verification fixture'}, actor, 201)
        cid = response['club']['id']
        self.clubs.append(cid)
        assert response['status'] == 'PENDING_APPROVAL'
        return cid

    def action(self, label, cid, action, actor='admin', reason=None, status=200):
        data = {'version': self.club(cid)['version'], 'reason': reason}
        prefix = '/api/admin/clubs/' if action in ['approve', 'reject', 'suspend', 'restore'] else '/api/clubs/'
        return self.expect(label, 'POST', prefix + str(cid) + '/' + action, data, actor, status)

    def decision(self, label, cid, target, action, actor, reason=None, status=200):
        m = self.member(cid, target)
        prefix = '/api/admin/clubs/' if action in ['suspend', 'restore'] else '/api/clubs/'
        return self.expect(label, 'POST', prefix + str(cid) + '/memberships/' + str(m['id']) + '/' + action,
                           {'version': m['version'], 'reason': reason}, actor, status)

    def api(self):
        self.phase = 'live API and authorization'
        a = self.create_club('A', 'owner')
        b = self.create_club('B', 'other')
        self.a, self.b = a, b
        self.expect('Pending club hidden from unrelated student', 'GET', '/api/clubs/' + str(a), actor='first', status=404)
        self.expect('Student cannot approve own club', 'POST', '/api/admin/clubs/' + str(a) + '/approve',
                    {'version': 0}, 'owner', 403)
        self.expect('OWNER cannot leave before transfer', 'POST', '/api/clubs/' + str(a) + '/membership/leave',
                    {'version': 0}, 'owner', 409)
        self.expect('Admin review queue', 'GET', '/api/admin/clubs?status=PENDING_APPROVAL', actor='admin')
        self.action('Approve A', a, 'approve')
        self.action('Approve B', b, 'approve')
        self.expect('Global CLUB_ADMIN cannot edit another club', 'PATCH', '/api/clubs/' + str(b),
                    {'version': 1, 'name': 'Forbidden change'}, 'owner', 403)
        self.expect('Other club OWNER cannot read roster', 'GET', '/api/clubs/' + str(a) + '/memberships', actor='other', status=403)
        self.expect('Other university cannot apply', 'POST', '/api/clubs/' + str(a) + '/membership-requests', {}, 'outsider', 409)
        assert self.sql('SELECT count(*) FROM public.club_memberships WHERE club_id=' + str(a)
                        + ' AND user_id=' + str(self.users['outsider']['id']) + ';') == '0'
        self.record('Rejected cross-university request persisted no membership')
        self.parallel('Concurrent duplicate applications: one 201, one 409', [
            ('POST', '/api/clubs/' + str(a) + '/membership-requests', {}, 'first'),
            ('POST', '/api/clubs/' + str(a) + '/membership-requests', {}, 'first')], [201, 409])
        self.expect('Second member applies', 'POST', '/api/clubs/' + str(a) + '/membership-requests', {}, 'second', 201)
        self.decision('OWNER approves first', a, 'first', 'approve', 'owner')
        self.decision('OWNER approves second', a, 'second', 'approve', 'owner')
        m = self.member(a, 'second')
        self.expect('MEMBER cannot assign manager', 'PATCH', '/api/clubs/' + str(a) + '/memberships/' + str(m['id']) + '/role',
                    {'version': m['version'], 'role': 'MANAGER'}, 'first', 403)
        for actor in ['first', 'second']:
            m = self.member(a, actor)
            self.expect('Promote ' + actor + ' to MANAGER', 'PATCH', '/api/clubs/' + str(a) + '/memberships/' + str(m['id']) + '/role',
                        {'version': m['version'], 'role': 'MANAGER'}, 'owner')
        assert self.sql("SELECT count(*) FROM public.club_memberships WHERE club_id=" + str(a) + " AND role='MANAGER' AND status='ACTIVE';") == '2'
        self.record('Multiple active MANAGER memberships persist')
        self.action('MANAGER cannot archive', a, 'archive', 'first', status=403)
        target = self.member(b, 'other')
        self.expect('Cross-club membership ID rejected', 'POST', '/api/clubs/' + str(a) + '/memberships/' + str(target['id']) + '/remove',
                    {'version': target['version'], 'reason': 'Must not affect another club'}, 'owner', 404)
        self.expect('OWNER may join another club', 'POST', '/api/clubs/' + str(a) + '/membership-requests', {}, 'other', 201)
        self.decision('MANAGER approves another student', a, 'other', 'approve', 'first')
        roster = self.expect('Manager paginated roster/projection', 'GET', '/api/clubs/' + str(a) + '/memberships?size=100', actor='first')
        for row in roster['content']:
            assert not {'password', 'passwordHash', 'email', 'bio', 'user'} & set(row)
        status, _, headers = self.http('GET', '/api/me/club-memberships', actor='first')
        assert status == 200 and 'no-store' in headers.get('Cache-Control', '')
        self.record('Private DTO and cache protection')
        self.expect('Paginated catalog with real nullable query parameter', 'GET', '/api/clubs?size=100&query=Live', actor='first')
        self.expect('Invalid page size', 'GET', '/api/clubs?size=101', actor='first', status=400)
        self.expect('Private review denied to ordinary member', 'GET', '/api/clubs/' + str(a) + '/review', actor='other', status=403)
        version = self.club(a)['version']
        self.parallel('Concurrent same-version club edits: one 200, one 409', [
            ('PATCH', '/api/clubs/' + str(a), {'version': version, 'name': 'Owner parallel edit'}, 'owner'),
            ('PATCH', '/api/clubs/' + str(a), {'version': version, 'name': 'Manager parallel edit'}, 'first')], [200, 409])
        self.expect('Profile university change blocked by membership', 'PUT', '/api/me/student-profile',
                    {'version': 0, 'universityId': self.universities[1], 'department': 'Software Engineering', 'studyYear': 2}, 'owner', 409)

    def races(self):
        self.phase = 'real HTTP concurrency'
        a = self.a
        owner = self.member(a, 'owner')
        requests = []
        for actor in ['first', 'second']:
            target = self.member(a, actor)
            requests.append(('POST', '/api/clubs/' + str(a) + '/ownership-transfer',
                             {'targetMembershipId': target['id'], 'clubVersion': self.club(a)['version'],
                              'ownerMembershipVersion': owner['version'], 'targetMembershipVersion': target['version']}, 'owner'))
        barrier = threading.Barrier(2)
        def transfer(args):
            barrier.wait(timeout=5)
            return self.http(*args)
        responses = [f.result(timeout=25) for f in [self.pool.submit(transfer, r) for r in requests]]
        statuses = sorted(r[0] for r in responses)
        assert statuses in ([200, 403], [200, 409]), 'Unexpected concurrent ownership transfer result'
        owner_id = int(self.sql("SELECT user_id FROM public.club_memberships WHERE club_id=" + str(a) + " AND role='OWNER' AND status='ACTIVE';"))
        self.owner = next(actor for actor in ['first', 'second'] if self.users[actor]['id'] == owner_id)
        self.loser = 'second' if self.owner == 'first' else 'first'
        assert self.member(a, 'owner')['role'] == 'MANAGER'
        self.record('Concurrent ownership transfers preserve exactly one OWNER')
        self.expect('Old OWNER can leave after transfer', 'POST', '/api/clubs/' + str(a) + '/membership/leave',
                    {'version': self.member(a, 'owner')['version']}, 'owner')
        assert self.sql("SELECT count(*) FROM public.user_roles WHERE user_id=" + str(self.users['owner']['id']) + " AND role='CLUB_ADMIN';") == '0'
        self.expect('Same JWT loses management after leaving', 'PATCH', '/api/clubs/' + str(a),
                    {'version': self.club(a)['version'], 'name': 'Forbidden after leaving'}, 'owner', 403)
        self.expect('New OWNER cannot leave before transfer', 'POST', '/api/clubs/' + str(a) + '/membership/leave',
                    {'version': self.member(a, self.owner)['version']}, self.owner, 409)
        barrier = threading.Barrier(2)
        calls = [
            ('POST', '/api/clubs/' + str(a) + '/membership-requests', {}, 'racer'),
            ('PUT', '/api/me/student-profile', {'version': 0, 'universityId': self.universities[1],
             'department': 'Software Engineering', 'studyYear': 2}, 'racer')]
        def run(args):
            barrier.wait(timeout=5)
            return self.http(*args)
        responses = [f.result(timeout=25) for f in [self.pool.submit(run, args) for args in calls]]
        assert (responses[0][0], responses[1][0]) in [(201, 409), (409, 200)], 'University/application race violated invariant'
        assert self.sql('SELECT count(*) FROM public.club_memberships m JOIN public.student_profiles p ON p.user_id=m.user_id '
                        'JOIN public.clubs c ON c.id=m.club_id WHERE m.user_id=' + str(self.users['racer']['id'])
                        + " AND m.status IN ('PENDING','ACTIVE','SUSPENDED') AND p.university_id<>c.university_id;") == '0'
        self.record('Concurrent profile change/application cannot cross universities')

    def stale_authority(self):
        self.phase = 'database lock and stale-authority race'
        actor = self.loser
        uid = self.users[actor]['id']
        mid = self.member(self.a, actor)['id']
        club_before = self.club(self.a)
        proc = subprocess.Popen(self.psql, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                env=self.env, text=True, bufsize=1)
        lines = queue.Queue()
        def consume():
            for line in proc.stdout:
                lines.put(line.strip())
        reader = threading.Thread(target=consume, daemon=True)
        reader.start()
        def marker(name, sql):
            proc.stdin.write(sql + '\n\\echo ' + name + '\n')
            proc.stdin.flush()
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                if lines.get(timeout=10) == name:
                    return
            raise AssertionError('Database lock handshake failed')
        try:
            marker('LOCKED', 'BEGIN; SET LOCAL lock_timeout=\'5s\'; SELECT id FROM public.users WHERE id=' + str(uid)
                   + ' FOR UPDATE; SELECT id FROM public.clubs WHERE id=' + str(self.a) + ' FOR UPDATE;'
                   + " UPDATE public.club_memberships SET role='MEMBER',version=version+1,updated_at=clock_timestamp() WHERE id=" + str(mid)
                   + " AND user_id=" + str(uid) + "; DELETE FROM public.user_roles WHERE user_id=" + str(uid) + " AND role='CLUB_ADMIN';")
            future = self.pool.submit(self.http, 'PATCH', '/api/clubs/' + str(self.a),
                                      {'version': club_before['version'], 'name': 'Must not survive revoked management'}, actor)
            waiting = False
            for _ in range(60):
                waiting = int(self.sql("SELECT count(*) FROM pg_stat_activity WHERE datname='campus_plus' AND application_name='"
                                       + self.app_name + "' AND wait_event_type='Lock';")) > 0
                if waiting:
                    break
                assert not future.done(), 'API unexpectedly completed before held user lock'
                time.sleep(0.05)
            assert waiting, 'Did not observe PostgreSQL lock wait for live request'
            marker('COMMITTED', 'COMMIT;')
            status, _, _ = future.result(timeout=20)
            assert status == 403 and self.club(self.a) == club_before, 'Stale permission survived lock/refresh'
            self.record('Observed real PostgreSQL lock wait; revoked manager request returns 403 without writes')
        finally:
            if proc.poll() is None:
                try:
                    proc.stdin.write('ROLLBACK;\n\\q\n')
                    proc.stdin.flush()
                    proc.wait(timeout=5)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    proc.kill()
                    proc.wait()

    def constraints(self):
        self.phase = 'PostgreSQL constraints and rollback'
        cid = str(self.a)
        uid = str(self.users[self.owner]['id'])
        mid = str(self.member(self.a, self.loser)['id'])
        owner_mid = str(self.member(self.a, self.owner)['id'])
        before = self.sql('SELECT id,role,status,version FROM public.club_memberships WHERE club_id=' + cid + ' ORDER BY id;')
        self.sql("BEGIN; UPDATE public.club_memberships SET role='OWNER' WHERE id=" + mid + '; COMMIT;', True, '23505')
        self.record('Partial unique index rejects a second active OWNER')
        self.sql("BEGIN; UPDATE public.club_memberships SET role='INVALID' WHERE id=" + mid + '; COMMIT;', True, '23514')
        self.record('CHECK constraint rejects invalid role')
        self.sql("BEGIN; UPDATE public.club_memberships SET club_id=9223372036854775807 WHERE id=" + mid + '; COMMIT;', True, '23503')
        self.record('Foreign key rejects nonexistent club')
        self.sql('BEGIN; INSERT INTO public.club_memberships(club_id,user_id,requested_at,created_at,updated_at) VALUES('
                 + cid + ',' + uid + ',now(),now(),now()); COMMIT;', True, '23505')
        self.record('Unique club/user constraint rejects duplicate membership')
        self.sql("BEGIN; UPDATE public.club_memberships SET role='MANAGER',version=version+1 WHERE id=" + owner_mid
                 + "; UPDATE public.club_memberships SET role='OWNER',version=version+1 WHERE id=" + mid
                 + "; UPDATE public.club_memberships SET role='OWNER' WHERE id=" + owner_mid + '; COMMIT;', True, '23505')
        assert self.sql('SELECT id,role,status,version FROM public.club_memberships WHERE club_id=' + cid + ' ORDER BY id;') == before
        self.record('Actual PostgreSQL failure rolls back the entire multi-step ownership change')

    def lifecycle_and_finish(self):
        self.phase = 'lifecycle and fixture retirement'
        self.decision('Admin suspends member', self.a, self.loser, 'suspend', 'admin', 'Fixture moderation')
        restored = self.decision('Admin restores only MEMBER privileges', self.a, self.loser, 'restore', 'admin')
        assert restored['role'] == 'MEMBER'
        self.decision('Owner removes member', self.a, self.loser, 'remove', self.owner, 'Fixture removal')
        m = self.member(self.a, self.loser)
        self.expect('Removed member cannot reapply', 'POST', '/api/clubs/' + str(self.a) + '/membership-requests',
                    {'version': m['version']}, self.loser, 409)
        self.decision('Owner permits reapplication', self.a, self.loser, 'allow-reapplication', self.owner)
        m = self.member(self.a, self.loser)
        self.expect('Reapplication cooldown enforced', 'POST', '/api/clubs/' + str(self.a) + '/membership-requests',
                    {'version': m['version']}, self.loser, 409)
        self.action('Admin suspends club', self.b, 'suspend', reason='Fixture moderation')
        self.expect('Cannot apply to suspended club', 'POST', '/api/clubs/' + str(self.b) + '/membership-requests', {}, 'first', 409)
        self.action('Admin restores club', self.b, 'restore')
        c = self.create_club('C', 'other')
        self.action('Admin rejects club', c, 'reject', reason='Fixture review')
        self.action('Owner resubmits rejected club', c, 'resubmit', 'other')
        self.action('Admin approves resubmission', c, 'approve')
        # Two different users race for the same university-scoped slug.
        create = {'name': 'Parallel creation', 'slug': 'live-' + self.tag + '-race', 'description': 'Unique slug race fixture'}
        responses = self.parallel('Concurrent duplicate slug: one 201, one 409', [
            ('POST', '/api/clubs', create, self.owner), ('POST', '/api/clubs', create, 'other')], [201, 409])
        self.clubs.append(next(r[1]['club']['id'] for r in responses if r[0] == 201))
        for cid in self.clubs:
            self.action('Archive only fixture club ' + str(cid), cid, 'archive', reason='Live verification finished')
        for uni in self.universities:
            detail = self.expect('Read fixture university', 'GET', '/api/universities/' + str(uni), actor='admin')
            self.expect('Deactivate only fixture university ' + str(uni), 'PATCH', '/api/universities/' + str(uni),
                        {'version': detail['version'], 'active': False}, 'admin')
        self.revoke_admin()
        self.expect('Same JWT loses temporary ADMIN', 'GET', '/api/admin/clubs', actor='admin', status=403)
        for actor in self.users:
            assert self.sql('SELECT role FROM public.user_roles WHERE user_id=' + str(self.users[actor]['id']) + ' ORDER BY role;') == 'STUDENT'
        self.record('All fixture accounts retain only STUDENT; no permanent elevated roles')

    def revoke_admin(self):
        if self.admin_granted:
            user = self.users['admin']
            self.sql("DELETE FROM public.user_roles r USING public.users u WHERE r.user_id=u.id AND u.id=" + str(user['id'])
                     + " AND u.email='" + user['email'] + "' AND r.role='ADMIN';", write=True)
            assert self.sql("SELECT count(*) FROM public.user_roles WHERE user_id=" + str(user['id']) + " AND role='ADMIN';") == '0'
            self.admin_granted = False
            self.record('Temporary fixture ADMIN revoked')

    def verify_preserved(self):
        after = self.snapshots()
        for table, rows in self.before.items():
            assert all(after[table].get(key) == value for key, value in rows.items()), 'Existing ' + table + ' row changed'
        old_users = set(self.before['users'])
        assert {key: value for key, value in after['user_roles'].items()
                if key.split(',')[0].strip('(') in old_users} == self.before['user_roles'], 'Existing account roles changed'
        self.record('All original users/password hashes/roles/universities/profiles preserved')

    def run(self):
        passed = False
        try:
            self.start()
            self.fixtures()
            self.api()
            self.races()
            self.stale_authority()
            self.constraints()
            self.lifecycle_and_finish()
            self.verify_preserved()
            assert os.environ['JWT_SECRET'].encode() not in self.log_path.read_bytes(), 'Secret detected in protected application log'
            passed = True
        finally:
            try:
                self.revoke_admin()
            finally:
                self.pool.shutdown(wait=True, cancel_futures=True)
                if self.app:
                    self.app.terminate()
                    try:
                        self.app.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        self.app.kill()
                        self.app.wait()
                    self.log.close()
                result = {'passed': passed, 'phase': self.phase, 'checks': self.checks,
                          'fixtureUserIds': {key: user['id'] for key, user in self.users.items()},
                          'fixtureUniversityIds': self.universities, 'fixtureClubIds': self.clubs,
                          'temporaryAdminRemoved': not self.admin_granted}
                fd = os.open(self.result_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
                os.fchmod(fd, 0o600)
                with os.fdopen(fd, 'w') as output:
                    json.dump(result, output, indent=2)
        print('SUCCESS: ' + str(len(self.checks)) + ' real PostgreSQL/API checks; fixtures retained; no commit/push.', flush=True)


if __name__ == '__main__':
    LiveChecks().run()
