import importlib.util
import contextlib
import io
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ci_postgres', ROOT / 'scripts/ci_postgres.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class CiPostgresBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.environment = dict(GITHUB_ACTIONS='true', RUNNER_ENVIRONMENT='github-hosted',
                                RUNNER_OS='Linux', RUNNER_TEMP='/tmp/campus-ci-boundary-fixture',
                                GITHUB_ENV='/tmp/campus-ci-boundary-fixture/environment')

    def test_local_macos_and_self_hosted_runners_are_rejected_before_any_command(self):
        for system, changes in [('Darwin', {}), ('Linux', {'RUNNER_ENVIRONMENT': 'self-hosted'}),
                                ('Linux', {'GITHUB_ACTIONS': 'false'}), ('Linux', {'RUNNER_OS': 'macOS'})]:
            environment = dict(self.environment, **changes)
            with self.subTest(system=system, changes=changes), patch.object(ci.platform, 'system', return_value=system), \
                    patch.object(ci.subprocess, 'run') as run, patch.object(ci.subprocess, 'Popen') as popen, \
                    patch.object(ci.Path, 'mkdir') as mkdir:
                with self.assertRaises(RuntimeError):
                    ci.setup(environment)
                run.assert_not_called()
                popen.assert_not_called()
                mkdir.assert_not_called()

    def test_missing_relative_root_and_checkout_paths_cannot_host_the_cluster(self):
        for name, value in [('RUNNER_TEMP', ''), ('GITHUB_ENV', ''), ('RUNNER_TEMP', 'relative'),
                            ('GITHUB_ENV', 'relative'), ('RUNNER_TEMP', '/'),
                            ('RUNNER_TEMP', str(ROOT)), ('RUNNER_TEMP', str(ROOT / 'target/ci'))]:
            with self.subTest(name=name, value=value), self.assertRaises(RuntimeError):
                ci.check_environment(dict(self.environment, **{name: value}), 'Linux')

    def test_existing_cluster_is_never_reused_or_automatically_deleted(self):
        with patch.object(ci.platform, 'system', return_value='Linux'), \
                patch.object(ci.Path, 'mkdir', side_effect=FileExistsError), \
                patch.object(ci.subprocess, 'run') as run:
            with self.assertRaises(FileExistsError):
                ci.setup(self.environment)
            run.assert_not_called()

    def test_only_hosted_linux_with_external_temporary_directory_is_accepted(self):
        directory, export = ci.check_environment(self.environment, 'Linux')
        self.assertEqual(directory, Path(self.environment['RUNNER_TEMP']).resolve())
        self.assertEqual(export, Path(self.environment['GITHUB_ENV']))

    def test_private_password_file_has_restricted_permissions_and_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'password'
            ci.private_file(path, 'fixture-secret')
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            with self.assertRaises(FileExistsError):
                ci.private_file(path, 'replacement')
            self.assertEqual(path.read_text(), 'fixture-secret')

    def test_hidden_password_prompt_protocol_without_a_database(self):
        # A tiny fake terminal peer verifies the psql conversation, with no SQL/DB execution.
        program = '''import sys
def prompt(value):
    sys.stdout.write(value); sys.stdout.flush()
prompt('CI_READY> ')
assert sys.stdin.readline().strip() == "SET password_encryption='scram-sha-256';"
prompt('CI_READY> ')
assert sys.stdin.readline().strip() == '\\\\password campus_plus_test_runner'
prompt('Enter new password for user "campus_plus_test_runner": ')
first = sys.stdin.readline().strip()
prompt('Enter it again: ')
assert sys.stdin.readline().strip() == first
assert first == 'terminal-secret-canary'
prompt('CI_READY> ')
assert sys.stdin.readline().strip().startswith('BEGIN; ALTER ROLE ')
prompt('CI_READY> ')
assert sys.stdin.readline().strip() == '\\\\q'
'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fake_psql.py'
            path.write_text(program)
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                ci.assign_passwords([sys.executable, path], dict(os.environ, LC_ALL='C'),
                                    {'campus_plus_test_runner': 'terminal-secret-canary'})
            self.assertNotIn('terminal-secret-canary', output.getvalue())

    def test_terminal_error_details_are_not_exposed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fake_failure.py'
            path.write_text("print('ERROR: terminal-secret-canary', flush=True)\n")
            with self.assertRaises(RuntimeError) as error:
                ci.assign_passwords([sys.executable, path], dict(os.environ, LC_ALL='C'), {})
            self.assertNotIn('terminal-secret-canary', str(error.exception))


if __name__ == '__main__':
    unittest.main()
