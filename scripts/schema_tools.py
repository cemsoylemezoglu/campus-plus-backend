#!/usr/bin/env python3
"""Read-only Campus+ schema inspection and offline structural comparison; never migrates."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent
TABLES = {'users', 'user_roles', 'universities', 'student_profiles', 'clubs', 'club_memberships'}
TABLES_BY_VERSION = {
    5: frozenset(TABLES),
    6: frozenset(TABLES | {'events', 'event_participations'}),
    7: frozenset(TABLES | {'events', 'event_participations', 'club_announcements'}),
    8: frozenset(TABLES | {'events', 'event_participations', 'club_announcements', 'notifications'}),
}

# Manually reviewed on PostgreSQL 17.11 during the first empty V005 installation.
# Re-parsing pg_get_constraintdef can push an array-wide text cast into each literal.
# These exact four pairs are equivalent; no general SQL/text normalization is allowed.
REVIEWED_CHECKS = {
    ('user_roles', 'ck_user_roles_role'): ('role', ('STUDENT', 'CLUB_ADMIN', 'ADMIN')),
    ('clubs', 'ck_clubs_status'): ('status', ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'ARCHIVED')),
    ('club_memberships', 'ck_club_memberships_role'): ('role', ('MEMBER', 'MANAGER', 'OWNER')),
    ('club_memberships', 'ck_club_memberships_status'): ('status', ('PENDING', 'ACTIVE', 'REJECTED', 'LEFT', 'REMOVED', 'SUSPENDED')),
}


def reviewed_check_spellings(column, values):
    literals = ["'" + value + "'::character varying" for value in values]
    original = 'CHECK (((' + column + ')::text = ANY ((ARRAY[' + ', '.join(literals) + '])::text[])))'
    reparsed = 'CHECK (((' + column + ')::text = ANY (ARRAY[' + ', '.join('(' + x + ')::text' for x in literals) + '])))'
    return original, reparsed


def comparison_schema(schema):
    result = copy.deepcopy(schema)
    for table in result['tables']:
        for constraint in table['constraints']:
            pair = REVIEWED_CHECKS.get((table['name'], constraint['name']))
            if pair is not None and constraint['kind'] == 'c':
                original, reparsed = reviewed_check_spellings(*pair)
                if constraint['definition'] == reparsed:
                    constraint['definition'] = original
    return result


def configuration(environment, target):
    if target not in ('existing', 'test'):
        raise ValueError('Unknown Campus+ inspection target.')
    prefix = '' if target == 'existing' else 'TEST_'
    database = 'campus_plus' if target == 'existing' else 'campus_plus_test'
    values = {}
    for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD'):
        name = prefix + key
        value = environment.get(name)
        if not value or not value.strip():
            raise ValueError(name + ' must be provided separately; no credential fallback is allowed.')
        values[key] = value
    raw_url = values['DB_URL']
    if not raw_url.startswith('jdbc:postgresql://'):
        raise ValueError('Only the authorized Campus+ JDBC target is allowed.')
    try:
        url = urlparse(raw_url.removeprefix('jdbc:'))
        valid = (url.scheme == 'postgresql' and url.hostname in ('127.0.0.1', 'localhost')
                 and url.port == 5434 and url.path == '/' + database and not url.query
                 and not url.fragment and url.username is None and url.password is None
                 and raw_url == 'jdbc:postgresql://' + url.hostname + ':5434/' + database)
    except ValueError:
        valid = False
    if not valid:
        raise ValueError('Only the authorized Campus+ JDBC target is allowed; URL details withheld.')
    return database, values['DB_USERNAME'], values['DB_PASSWORD']


def read_document(environment, target, psql, sql_file):
    database, username, password = configuration(environment, target)
    env = dict(environment)
    # In particular PGHOSTADDR can override routing even with an explicit -h host.
    # Sanitize this child process only; preserve the caller's environment.
    for key in list(env):
        if key.startswith('PG'):
            env.pop(key)
    env.update(PGPASSWORD=password, PGCONNECT_TIMEOUT='5',
               PGPASSFILE='/dev/null',
               PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=10000 -c lock_timeout=3000')
    args = [psql, '-X', '-w', '-q', '-A', '-t', '-h', '127.0.0.1', '-p', '5434',
            '-U', username, '-d', database, '-v', 'ON_ERROR_STOP=1']

    def query(statement):
        result = subprocess.run(args, input=statement, env=env, text=True, capture_output=True, timeout=20)
        if result.returncode:
            raise RuntimeError('Read-only Campus+ query failed; driver/credential details withheld.')
        return json.loads(result.stdout.strip())

    identity_sql = "SELECT json_build_object('database',current_database(),'address',host(inet_server_addr())," \
                   "'port',inet_server_port(),'serverVersion',current_setting('server_version_num')::int," \
                   "'readOnly',current_setting('transaction_read_only'));"
    validate_identity(query(identity_sql), database)
    sql = (ROOT / sql_file).read_text()
    inventory = query('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\n'
                      + 'SET LOCAL search_path=public,pg_catalog;\n' + sql + '\nCOMMIT;')
    validate_identity(inventory['identity'], database)
    return inventory


def inspect(environment, target, psql, schema_version=5):
    if schema_version not in TABLES_BY_VERSION:
        raise ValueError('Only explicitly reviewed Campus+ schema versions can be inspected.')
    inventory = read_document(environment, target, psql, 'db/verification/schema_inventory.sql')
    if {table['name'] for table in inventory['schema']['tables']} != TABLES_BY_VERSION[schema_version]:
        raise RuntimeError('Unexpected public tables for the reviewed schema version; inspect before any migration operation.')
    return inventory


def verify_files(root=ROOT):
    manifest = json.loads((root / 'db/schema/legacy-checksums.json').read_text())
    files = {path.name for path in (root / 'db/migrations').glob('*.sql')}
    if files != set(manifest):
        raise ValueError('Historical migration set differs from the reviewed manifest.')
    for name, expected in manifest.items():
        if hashlib.sha256((root / 'db/migrations' / name).read_bytes()).hexdigest() != expected:
            raise ValueError('Historical migration checksum mismatch; do not repair or reapply.')


def verify_managed_checksums(contract, root=ROOT):
    expected = contract.get('migrationChecksums')
    if expected is None:
        return  # Original V005 contract predates the versioned SQL checksum metadata.
    if not expected or any(not re.fullmatch(r'V[0-9]+__[A-Za-z0-9_]+\.sql', name) for name in expected):
        raise ValueError('Invalid managed migration checksum manifest.')
    directory = root / 'db/managed-migrations'
    if {path.name for path in directory.glob('*.sql')} != set(expected):
        raise ValueError('Managed SQL migration set differs from the reviewed contract.')
    for name, digest in expected.items():
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Managed SQL checksum mismatch; do not edit an applied migration.')


def validate_identity(identity, database):
    if not (identity.get('database') == database and identity.get('address') == '127.0.0.1'
            and identity.get('port') == 5434 and 170000 <= identity.get('serverVersion', 0) < 180000
            and identity.get('readOnly') == 'on'):
        raise RuntimeError('Unauthorized target, PostgreSQL version or read-only state.')


def compare(expected, actual):
    # Identity, ownership, row counts and sequence positions are intentionally not schema contracts.
    # Constraint names/definitions, FK actions, defaults, identities and index predicates are compared.
    if (comparison_schema(expected['schema']) != comparison_schema(actual['schema'])
            or expected.get('databaseEnvironment') != actual.get('databaseEnvironment')):
        raise ValueError('Schema does not match the reviewed contract; migration adoption must not proceed.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    inventory = commands.add_parser('inspect')
    inventory.add_argument('--target', choices=('existing', 'test'), required=True)
    inventory.add_argument('--schema-version', type=int, choices=(5, 6, 7, 8), default=5,
                           help='Expected table set only; structural contract comparison is still required.')
    inventory.add_argument('--psql', default='psql')
    inventory.add_argument('--output', type=Path, required=True)
    comparison = commands.add_parser('compare')
    comparison.add_argument('--expected', type=Path, required=True)
    comparison.add_argument('--actual', type=Path, required=True)
    commands.add_parser('verify-legacy')
    fingerprints = commands.add_parser('fingerprints')
    fingerprints.add_argument('--target', choices=('existing', 'test'), required=True)
    fingerprints.add_argument('--psql', default='psql')
    fingerprints.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.command in ('inspect', 'fingerprints'):
        if args.command == 'inspect':
            data = inspect(os.environ, args.target, args.psql, args.schema_version)
        else:
            data = read_document(os.environ, args.target, args.psql, 'db/verification/data_fingerprints.sql')
        # Do not overwrite an earlier inspection unnoticed.
        fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as output:
            json.dump(data, output, indent=2, sort_keys=True)
            output.write('\n')
        print('Read-only Campus+ report written; no database changes.')
    elif args.command == 'compare':
        expected = json.loads(args.expected.read_text())
        verify_managed_checksums(expected)
        compare(expected, json.loads(args.actual.read_text()))
        print('Schema matches reviewed contract.')
    else:
        verify_files()
        print('Historical V001-V005 migration checksums unchanged.')


if __name__ == '__main__':
    main()
