#!/usr/bin/env python3
"""Create a disposable private PG17 cluster ONLY on a GitHub-hosted Linux runner."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import platform
import pty
import secrets
import select
import shlex
import subprocess
import termios
import time

ROOT = Path(__file__).resolve().parent.parent
PG_BIN = Path('/usr/lib/postgresql/17/bin')


def check_environment(environment, system):
    if (system != 'Linux' or environment.get('GITHUB_ACTIONS') != 'true'
            or environment.get('RUNNER_ENVIRONMENT') != 'github-hosted'
            or environment.get('RUNNER_OS') != 'Linux'):
        raise RuntimeError('CI provisioning requires a disposable GitHub-hosted Linux runner; local execution refused.')
    temporary = environment.get('RUNNER_TEMP')
    export = environment.get('GITHUB_ENV')
    if not temporary or not export:
        raise RuntimeError('GitHub runner temporary directory and environment file are required.')
    if not Path(temporary).is_absolute() or not Path(export).is_absolute():
        raise RuntimeError('CI runner paths must be absolute.')
    base = Path(temporary).resolve()
    if base == Path('/') or base == ROOT or ROOT in base.parents:
        raise RuntimeError('CI cluster must be outside the source checkout.')
    return base, Path(export)


def private_file(path, contents):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as output:
        output.write(contents)


def run(args, environment, sql=None, timeout=120):
    result = subprocess.run([str(x) for x in args], env=environment, input=sql,
                            text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        # Do not echo SQL, generated credentials, server logs or libpq driver details.
        raise RuntimeError('Private CI PostgreSQL command failed; no automatic retry/reset performed.')
    return result.stdout


def assign_passwords(args, environment, passwords):
    # psql computes the SCRAM verifier; passwords are hidden terminal input, never SQL literals.
    master, slave = pty.openpty()
    settings = termios.tcgetattr(slave)
    settings[3] &= ~termios.ECHO
    termios.tcsetattr(slave, termios.TCSANOW, settings)

    def terminal():
        os.setsid()
        fcntl.ioctl(0, termios.TIOCSCTTY, 0)

    child = subprocess.Popen([str(x) for x in args] + ['-v', 'PROMPT1=CI_READY> '], env=environment,
                             stdin=slave, stdout=slave, stderr=slave, preexec_fn=terminal)
    os.close(slave)
    pending = b''

    def expect(token):
        nonlocal pending
        deadline = time.monotonic() + 15
        while token not in pending:
            if time.monotonic() > deadline:
                raise RuntimeError('CI password prompt timed out; details withheld.')
            if select.select([master], [], [], 0.2)[0]:
                chunk = os.read(master, 65536)
                if not chunk:
                    raise RuntimeError('CI password session ended unexpectedly.')
                pending += chunk
            if b'ERROR:' in pending or b'FATAL:' in pending:
                raise RuntimeError('CI password assignment failed; details withheld.')
        _, pending = pending.split(token, 1)

    def send(value):
        os.write(master, value.encode() + b'\n')

    try:
        expect(b'CI_READY> ')
        send("SET password_encryption='scram-sha-256';")
        expect(b'CI_READY> ')
        for role, password in passwords.items():
            send('\\password ' + role)
            expect(b'Enter new password for user "' + role.encode() + b'": ')
            send(password)
            expect(b'Enter it again: ')
            send(password)
            expect(b'CI_READY> ')
        send('BEGIN; ALTER ROLE campus_plus_test_migrator LOGIN; ALTER ROLE campus_plus_test_runner LOGIN; COMMIT;')
        expect(b'CI_READY> ')
        send('\\q')
        if child.wait(timeout=10):
            raise RuntimeError('CI password session failed.')
    finally:
        if child.poll() is None:
            child.terminate()
            child.wait(timeout=5)
        os.close(master)


def setup(environment):
    base, export = check_environment(environment, platform.system())
    directory = base / 'campus-plus-pg17'
    directory.mkdir(mode=0o700)  # Fail on an existing cluster; never reuse/clean/drop.
    admin = secrets.token_urlsafe(48)
    passwords = {role: secrets.token_urlsafe(48) for role in
                 ('campus_plus_test_migrator', 'campus_plus_test_runner')}
    for value in (admin, *passwords.values()):
        print('::add-mask::' + value, flush=True)
    private_file(directory / 'admin-password', admin)
    env = {k: v for k, v in environment.items() if not k.startswith('PG')}
    env.update(PGPASSFILE='/dev/null', PGCONNECT_TIMEOUT='5', PSQL_HISTORY='/dev/null', LC_ALL='C',
               PGOPTIONS='-c statement_timeout=10000 -c lock_timeout=3000')
    run([PG_BIN / 'initdb', '-D', directory / 'data', '-U', 'campus_ci_owner',
         '--auth-local=trust', '--auth-host=scram-sha-256', '--encoding=UTF8',
         '--locale-provider=libc', '--locale=en_US.UTF-8', '--pwfile', directory / 'admin-password'], env)
    run([PG_BIN / 'pg_ctl', '-D', directory / 'data', '-l', directory / 'server.log',
         '-o', '-h 127.0.0.1 -p 5434 -k ' + shlex.quote(str(directory))
         + ' -c log_statement=none -c log_min_error_statement=panic', '-w', 'start'], env)
    env['PGPASSWORD'] = admin
    args = [PG_BIN / 'psql', '-X', '-w', '-q', '-A', '-t', '-h', '127.0.0.1', '-p', '5434',
            '-U', 'campus_ci_owner', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1']
    identity = json.loads(run(args, env, "SELECT json_build_object('database',current_database(),"
        "'host',host(inet_server_addr()),'port',inet_server_port(),'version',current_setting('server_version_num')::int,"
        "'directory',current_setting('data_directory'),'role',current_user);"))
    if not (identity['database'] == 'postgres' and identity['host'] == '127.0.0.1' and identity['port'] == 5434
            and 170000 <= identity['version'] < 180000 and identity['role'] == 'campus_ci_owner'
            and Path(identity['directory']).resolve() == (directory / 'data').resolve()):
        raise RuntimeError('CI cluster identity mismatch; no provisioning performed.')
    run(args, env, (ROOT / 'scripts/ci/bootstrap.sql').read_text())
    # All further commands use only campus_plus_test, never a local/source application DB.
    args[args.index('-d') + 1] = 'campus_plus_test'
    run(args + ['-f', ROOT / 'db/provisioning/test/02-lockdown-schema.psql'], env)
    assign_passwords(args, env, passwords)
    values = dict(
        TEST_DB_URL='jdbc:postgresql://127.0.0.1:5434/campus_plus_test',
        TEST_DB_USERNAME='campus_plus_test_runner', TEST_DB_PASSWORD=passwords['campus_plus_test_runner'],
        MIGRATION_DB_URL='jdbc:postgresql://127.0.0.1:5434/campus_plus_test',
        MIGRATION_DB_USERNAME='campus_plus_test_migrator', MIGRATION_DB_PASSWORD=passwords['campus_plus_test_migrator'])
    with export.open('a') as output:
        for key, value in values.items():
            output.write(key + '=' + value + '\n')
    print('Disposable Campus+ PG17 test cluster ready; no local or application databases used.')


if __name__ == '__main__':
    argparse.ArgumentParser(description=__doc__).parse_args()
    try:
        setup(os.environ)
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        # Driver errors and captured terminal output are intentionally not exposed.
        raise SystemExit('CI PostgreSQL setup stopped: ' + (str(error) if isinstance(error, RuntimeError)
                                                          else 'OS/process failure; details withheld.'))
