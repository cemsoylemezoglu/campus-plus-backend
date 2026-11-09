"""Offline review gates. These do not execute or validate SQL against PostgreSQL."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
REVIEW = ROOT / 'db/review-migrations'
NAME = 'V012__add_direct_messaging.sql'
MIGRATION = ROOT / 'db/managed-migrations' / NAME
TABLES = {'direct_conversations', 'direct_messages', 'direct_conversation_states', 'user_blocks'}


class MessagingReviewTest(unittest.TestCase):
    def test_approved_migration_checksum_and_execution_stays_opt_in(self):
        import hashlib
        self.assertFalse((REVIEW / NAME).exists())
        self.assertEqual('5f08e14a190a3206ebca34e5d437498776f90ea4b9a1e0446737c428cb7125bf', hashlib.sha256(MIGRATION.read_bytes()).hexdigest())
        self.assertEqual('executeInTransaction=true\n', MIGRATION.with_suffix('.sql.conf').read_text())
        self.assertIn('<target>12</target>', (ROOT / 'pom.xml').read_text())
        self.assertIn('<flyway.skip>true</flyway.skip>', (ROOT / 'pom.xml').read_text())

    def test_no_old_table_or_row_changes_and_target_is_guarded(self):
        sql = re.sub(r'--[^\n]*', '', MIGRATION.read_text())
        self.assertEqual(TABLES, set(re.findall(r'CREATE TABLE (\w+)', sql)))
        self.assertNotRegex(sql.upper(), r'\b(ALTER|DROP|TRUNCATE|INSERT|UPDATE|DELETE|GRANT)\s+(TABLE|INTO|FROM|SCHEMA|DATABASE|USERS|PUBLIC\.)')
        for guard in ("inet_server_port() IS DISTINCT FROM 5434", "host(inet_server_addr()) IS DISTINCT FROM '127.0.0.1'",
                      "server_version_num')::int NOT BETWEEN 170000 AND 179999", "version IN ('11','011')", "lock_timeout='5s'", "statement_timeout='30s'"):
            self.assertIn(guard, sql)

    def test_forged_participants_are_constrained_without_triggers(self):
        sql = MIGRATION.read_text()
        self.assertIn('CHECK(sender_id IN(lower_user_id,higher_user_id))', sql)
        self.assertIn('CHECK(user_id IN(lower_user_id,higher_user_id))', sql)
        self.assertEqual(2, sql.count('FOREIGN KEY(conversation_id,lower_user_id,higher_user_id)'))
        self.assertIn('UNIQUE(lower_user_id,higher_user_id)', sql)
        self.assertIn('UNIQUE(conversation_id,sender_id,client_message_id)', sql)

    def test_grants_are_new_objects_only_and_messages_are_append_only(self):
        for target, role, database in (('test', 'campus_plus_test_runner', 'campus_plus_test'),
                                       ('main', 'campus_plus_user', 'campus_plus')):
            path = ROOT / ('db/provisioning/test/10-messaging-runtime-grants.psql' if target == 'test' else 'db/provisioning/main/07-messaging-runtime-grants.psql')
            text = path.read_text()
            grants = re.findall(r'^GRANT (.+?) ON (.+?) TO (\w+);$', text, re.M)
            self.assertEqual(3, len(grants))
            for permissions, objects, recipient in grants:
                self.assertEqual(role, recipient)
                self.assertNotRegex(permissions, r'DELETE|TRUNCATE|ALL')
                names = {name.strip().removeprefix('public.') for name in objects.removeprefix('SEQUENCE ').split(',')}
                if 'USAGE' in permissions:
                    self.assertEqual({t + '_id_seq' for t in TABLES}, names)
                    self.assertEqual('USAGE,SELECT', permissions)
                elif 'UPDATE' in permissions:
                    self.assertEqual(TABLES - {'direct_messages'}, names)
                else:
                    self.assertEqual({'direct_messages'}, names)
                    self.assertEqual('SELECT,INSERT', permissions)
            self.assertIn("current_database()<>'" + database + "'", text)
            self.assertNotIn('GRANT OPTION', text)
