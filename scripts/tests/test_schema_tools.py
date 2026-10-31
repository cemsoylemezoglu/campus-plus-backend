import copy
import importlib.util
import json
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('schema_tools', ROOT / 'scripts/schema_tools.py')
tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tools)


class SchemaToolsTest(unittest.TestCase):
    def setUp(self):
        self.env = {'DB_URL': 'jdbc:postgresql://127.0.0.1:5434/campus_plus',
                    'DB_USERNAME': 'fixture-user', 'DB_PASSWORD': 'fixture-canary'}
        self.identity = {'database': 'campus_plus', 'address': '127.0.0.1', 'port': 5434,
                         'serverVersion': 170011, 'readOnly': 'on'}
        self.contract = json.loads((ROOT / 'db/schema/v005-contract.json').read_text())

    def test_main_target_and_localhost_are_normalized_to_explicit_tcp_arguments(self):
        for host in ('localhost', '127.0.0.1'):
            self.env['DB_URL'] = 'jdbc:postgresql://' + host + ':5434/campus_plus'
            self.assertEqual(tools.configuration(self.env, 'existing')[0], 'campus_plus')

    def test_test_credentials_never_fall_back_to_main_credentials(self):
        with self.assertRaisesRegex(ValueError, 'TEST_DB_URL'):
            tools.configuration(self.env, 'test')

    def test_missing_and_blank_credentials_fail_before_connecting(self):
        for name in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD'):
            for value in (None, '', ' '):
                with self.subTest(name=name, value=value):
                    env = dict(self.env)
                    env[name] = value
                    with self.assertRaises(ValueError):
                        tools.configuration(env, 'existing')

    def test_wrong_target_and_jdbc_overrides_never_connect_or_echo_the_url(self):
        for url in ('jdbc:postgresql://127.0.0.1:5432/campus_plus',
                    'jdbc:postgresql://127.0.0.1:5434/other_project',
                    'jdbc:postgresql://remote.example:5434/campus_plus',
                    'jdbc:postgresql://127.0.0.1:5434/campus_plus?password=url-canary',
                    'jdbc:postgresql://u:p@127.0.0.1:5434/campus_plus',
                    'jdbc:postgresql://127.0.0.1/campus_plus',
                    'jdbc:postgresql://127.0.0.1:not-a-port/campus_plus',
                    'jdbc:postgresql://127.0.0.1:5434/campus_plus#other',
                    'jdbc:h2:mem:campus_plus'):
            with self.subTest(url=url), patch.object(tools.subprocess, 'run') as run:
                self.env['DB_URL'] = url
                with self.assertRaises(ValueError) as error:
                    tools.inspect(self.env, 'existing', 'psql')
                run.assert_not_called()
                self.assertNotIn(url, str(error.exception))

    def test_only_separately_configured_test_target_is_accepted(self):
        self.env.update(TEST_DB_URL='jdbc:postgresql://127.0.0.1:5434/campus_plus_test',
                        TEST_DB_USERNAME='test-fixture', TEST_DB_PASSWORD='test-canary')
        self.assertEqual(tools.configuration(self.env, 'test'), ('campus_plus_test', 'test-fixture', 'test-canary'))
        self.env['TEST_DB_URL'] = self.env['DB_URL']
        with self.assertRaises(ValueError):
            tools.configuration(self.env, 'test')

    def test_read_only_session_and_nonleaking_process_arguments(self):
        report = dict(self.contract, identity=self.identity)
        self.env.update(PGOPTIONS='-c default_transaction_read_only=off', PGSERVICE='wrong-service',
                        PGHOSTADDR='192.0.2.9', PGDATABASE='wrong-target')
        responses = [subprocess.CompletedProcess([], 0, json.dumps(x), '') for x in (self.identity, report)]
        with patch.object(tools.subprocess, 'run', side_effect=responses) as run:
            self.assertEqual(tools.inspect(self.env, 'existing', 'psql'), report)
            for call in run.call_args_list:
                self.assertNotIn('fixture-canary', ' '.join(call.args[0]))
                self.assertIn('default_transaction_read_only=on', call.kwargs['env']['PGOPTIONS'])
                self.assertNotIn('PGSERVICE', call.kwargs['env'])
                self.assertNotIn('PGHOSTADDR', call.kwargs['env'])
                self.assertNotIn('PGDATABASE', call.kwargs['env'])
                self.assertEqual(call.kwargs['env']['PGPASSFILE'], '/dev/null')
                self.assertEqual(call.kwargs['env']['PGPASSWORD'], 'fixture-canary')
            self.assertIn('REPEATABLE READ READ ONLY', run.call_args_list[1].kwargs['input'])
            self.assertEqual(self.env['PGHOSTADDR'], '192.0.2.9')

    def test_wrong_actual_identity_prevents_catalogue_read(self):
        for change in ({'database': 'other_project'}, {'address': '127.0.0.2'}, {'port': 5432},
                       {'readOnly': 'off'}, {'serverVersion': 140000}, {'serverVersion': 180000}):
            identity = dict(self.identity, **change)
            with self.subTest(change=change), patch.object(tools.subprocess, 'run',
                    return_value=subprocess.CompletedProcess([], 0, json.dumps(identity), '')) as run:
                with self.assertRaises(RuntimeError):
                    tools.inspect(self.env, 'existing', 'psql')
                self.assertEqual(run.call_count, 1)

    def test_sql_failure_does_not_echo_driver_or_credentials(self):
        with patch.object(tools.subprocess, 'run',
                          return_value=subprocess.CompletedProcess([], 1, '', 'fixture-canary driver details')):
            with self.assertRaises(RuntimeError) as error:
                tools.inspect(self.env, 'existing', 'psql')
            self.assertNotIn('fixture-canary', str(error.exception))

    def test_additional_public_table_prevents_baseline_inventory(self):
        report = copy.deepcopy(dict(self.contract, identity=self.identity))
        report['schema']['tables'].append({'name': 'unexpected'})
        with patch.object(tools, 'read_document', return_value=report):
            with self.assertRaises(RuntimeError):
                tools.inspect(self.env, 'existing', 'psql')

    def test_contract_ignores_connection_identity_but_checks_structure(self):
        tools.compare(self.contract, dict(self.contract, identity={'database': 'campus_plus_test'}))

    def test_fk_index_column_and_sequence_drift_block_comparison(self):
        changes = [lambda s: s['tables'][0]['columns'][0].update(notNull=False),
                   lambda s: s['tables'][0]['columns'][0].update(default='unexpected'),
                   lambda s: s['tables'][0]['constraints'][0].update(definition='unexpected'),
                   lambda s: s['tables'][0]['indexes'][0].update(valid=False),
                   lambda s: s['sequences'][0].update(cycle=True),
                   lambda s: s['tables'].pop()]
        for change in changes:
            actual = copy.deepcopy(self.contract)
            change(actual['schema'])
            with self.assertRaises(ValueError):
                tools.compare(self.contract, actual)

    def test_historical_sql_checksums_match_reviewed_manifest(self):
        tools.verify_files()

    def managed_fixture(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        directory = root / 'db/managed-migrations'
        directory.mkdir(parents=True)
        path = directory / 'V006__reviewed.sql'
        path.write_text('-- reviewed SQL fixture; never executed\n')
        contract = {'migrationChecksums': {path.name: hashlib.sha256(path.read_bytes()).hexdigest()}}
        return root, path, contract

    def test_reviewed_managed_migration_bytes_match_the_contract(self):
        root, path, contract = self.managed_fixture()
        tools.verify_managed_checksums(contract, root)

    def test_editing_a_reviewed_managed_migration_is_rejected(self):
        root, path, contract = self.managed_fixture()
        path.write_text('-- modified fixture\n')
        with self.assertRaises(ValueError):
            tools.verify_managed_checksums(contract, root)

    def test_missing_or_extra_managed_migration_requires_a_new_review(self):
        root, path, contract = self.managed_fixture()
        extra = path.parent / 'V007__unreviewed.sql'
        extra.write_text('-- fixture\n')
        with self.assertRaises(ValueError):
            tools.verify_managed_checksums(contract, root)
        extra.unlink()
        path.unlink()
        with self.assertRaises(ValueError):
            tools.verify_managed_checksums(contract, root)

    def test_manifest_paths_cannot_escape_the_managed_migration_directory(self):
        root, path, contract = self.managed_fixture()
        contract['migrationChecksums'] = {'../outside.sql': 'unused'}
        with self.assertRaises(ValueError):
            tools.verify_managed_checksums(contract, root)

    def test_original_v005_contract_remains_supported_without_new_checksum_metadata(self):
        tools.verify_managed_checksums(self.contract)

    def test_v006_inspection_requires_explicit_version_and_exact_eight_tables(self):
        report = copy.deepcopy(dict(self.contract, identity=self.identity))
        report['schema']['tables'].extend([{'name': 'events'}, {'name': 'event_participations'}])
        with patch.object(tools, 'read_document', return_value=report):
            with self.assertRaises(RuntimeError):
                tools.inspect(self.env, 'existing', 'psql')
            self.assertEqual(tools.inspect(self.env, 'existing', 'psql', 6), report)

    def test_v006_inspection_does_not_accept_missing_or_unrelated_tables(self):
        for names in (['events'], ['events', 'event_participations', 'unexpected']):
            report = copy.deepcopy(dict(self.contract, identity=self.identity))
            report['schema']['tables'].extend({'name': name} for name in names)
            with patch.object(tools, 'read_document', return_value=report), self.assertRaises(RuntimeError):
                tools.inspect(self.env, 'existing', 'psql', 6)

    def test_v007_requires_explicit_version_and_exact_nine_table_set(self):
        report = copy.deepcopy(self.contract)
        report['schema']['tables'].extend({'name': name} for name in ('events', 'event_participations', 'club_announcements'))
        with patch.object(tools, 'read_document', return_value=report):
            self.assertEqual(tools.inspect(self.env, 'existing', 'psql', 7), report)
            for version in (5, 6):
                with self.assertRaises(RuntimeError):
                    tools.inspect(self.env, 'existing', 'psql', version)

    def test_v007_rejects_missing_event_or_additional_unrelated_table(self):
        for names in (('events', 'club_announcements'), ('events', 'event_participations', 'club_announcements', 'unexpected')):
            report = copy.deepcopy(self.contract)
            report['schema']['tables'].extend({'name': name} for name in names)
            with patch.object(tools, 'read_document', return_value=report):
                with self.assertRaises(RuntimeError):
                    tools.inspect(self.env, 'existing', 'psql', 7)

    def test_v008_requires_explicit_version_and_exact_ten_tables(self):
        report = copy.deepcopy(self.contract)
        report['schema']['tables'].extend({'name': name} for name in ('events', 'event_participations', 'club_announcements', 'notifications'))
        with patch.object(tools, 'read_document', return_value=report):
            self.assertEqual(tools.inspect(self.env, 'existing', 'psql', 8), report)
            for version in (5, 6, 7):
                with self.assertRaises(RuntimeError):
                    tools.inspect(self.env, 'existing', 'psql', version)

    def test_v008_rejects_missing_source_or_unrelated_extra_table(self):
        for names in (('events', 'event_participations', 'notifications'), ('events', 'event_participations', 'club_announcements', 'notifications', 'unexpected')):
            report = copy.deepcopy(self.contract)
            report['schema']['tables'].extend({'name': name} for name in names)
            with patch.object(tools, 'read_document', return_value=report):
                with self.assertRaises(RuntimeError):
                    tools.inspect(self.env, 'existing', 'psql', 8)

    def test_unknown_schema_version_never_connects(self):
        with patch.object(tools, 'read_document') as read:
            with self.assertRaises(ValueError):
                tools.inspect(self.env, 'existing', 'psql', 9)
            read.assert_not_called()

    def test_v006_table_allowlist_does_not_weaken_structural_v005_comparison(self):
        actual = copy.deepcopy(self.contract)
        actual['schema']['tables'].extend([{'name': 'events', 'constraints': []}, {'name': 'event_participations', 'constraints': []}])
        with self.assertRaises(ValueError):
            tools.compare(self.contract, actual)

    def test_encoding_or_database_collation_drift_blocks_adoption(self):
        actual = copy.deepcopy(self.contract)
        actual['databaseEnvironment'] = {'encoding': 'UTF8', 'collate': 'unexpected'}
        with self.assertRaises(ValueError):
            tools.compare(self.contract, actual)

    def test_only_four_reviewed_postgres_array_cast_spellings_are_equivalent(self):
        actual = copy.deepcopy(self.contract)
        count = 0
        for table in actual['schema']['tables']:
            for constraint in table['constraints']:
                pair = tools.REVIEWED_CHECKS.get((table['name'], constraint['name']))
                if pair is not None:
                    original, reparsed = tools.reviewed_check_spellings(*pair)
                    self.assertEqual(constraint['definition'], original)
                    constraint['definition'] = reparsed
                    count += 1
        before = copy.deepcopy(actual)
        self.assertEqual(count, 4)
        tools.compare(self.contract, actual)
        self.assertEqual(actual, before)  # Inspection evidence must remain unmodified.

    def test_changed_reviewed_check_values_operator_or_validation_still_fail(self):
        for table in self.contract['schema']['tables']:
            for constraint in table['constraints']:
                pair = tools.REVIEWED_CHECKS.get((table['name'], constraint['name']))
                if pair is None:
                    continue
                _, reparsed = tools.reviewed_check_spellings(*pair)
                for definition in (reparsed.replace("'" + pair[1][0] + "'", "'UNEXPECTED'"),
                                   reparsed.replace(' = ANY ', ' <> ALL ')):
                    actual = copy.deepcopy(self.contract)
                    target = next(c for t in actual['schema']['tables'] if t['name'] == table['name']
                                  for c in t['constraints'] if c['name'] == constraint['name'])
                    target['definition'] = definition
                    with self.assertRaises(ValueError):
                        tools.compare(self.contract, actual)
                actual = copy.deepcopy(self.contract)
                target = next(c for t in actual['schema']['tables'] if t['name'] == table['name']
                              for c in t['constraints'] if c['name'] == constraint['name'])
                target.update(definition=reparsed, validated=False)
                with self.assertRaises(ValueError):
                    tools.compare(self.contract, actual)

    def test_reviewed_spelling_under_a_different_constraint_name_is_not_accepted(self):
        actual = copy.deepcopy(self.contract)
        table = next(t for t in actual['schema']['tables'] if t['name'] == 'user_roles')
        constraint = next(c for c in table['constraints'] if c['name'] == 'ck_user_roles_role')
        constraint['name'] = 'unreviewed_check'
        constraint['definition'] = tools.reviewed_check_spellings(*tools.REVIEWED_CHECKS[('user_roles', 'ck_user_roles_role')])[1]
        with self.assertRaises(ValueError):
            tools.compare(self.contract, actual)


if __name__ == '__main__':
    unittest.main()
