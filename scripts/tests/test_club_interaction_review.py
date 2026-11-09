"""Offline guards for approved Campus+ V011 SQL and target-specific narrow grants."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
REVIEW = ROOT / 'db/review-migrations'
MANAGED = ROOT / 'db/managed-migrations'
MIGRATION = MANAGED / 'V011__add_club_content_interactions.sql'


class ClubInteractionMigrationReviewTest(unittest.TestCase):
    def test_old_notification_source_branches_are_preserved_verbatim(self):
        old = (ROOT / 'db/managed-migrations/V010__add_post_comments_and_likes.sql').read_text()
        new = MIGRATION.read_text()
        start = old.index(' ADD CONSTRAINT ck_notifications_source CHECK(') + len(' ADD CONSTRAINT ck_notifications_source CHECK(')
        end = old.index('\n );', start)
        self.assertIn('(club_comment_id IS NULL AND (' + old[start:end].strip() + '))', new)
        old_types = set(re.findall(r"'([A-Z_]+)'", old[old.index('CHECK(type IN ('):old.index(' ADD CONSTRAINT ck_notifications_source')]))
        new_types = set(re.findall(r"'([A-Z_]+)'", new[new.index('CHECK(type IN ('):new.index(' ADD CONSTRAINT ck_notifications_source')]))
        self.assertEqual(27, len(old_types))
        self.assertEqual(old_types | {kind + '_' + action for kind in ('EVENT', 'ANNOUNCEMENT')
                                     for action in ('COMMENTED', 'COMMENT_REPLIED', 'LIKED', 'COMMENT_LIKED')}, new_types)

    def test_approved_sql_matches_reviewed_checksum_and_stays_opt_in(self):
        name = 'V011__add_club_content_interactions.sql'
        import hashlib
        self.assertFalse((REVIEW / name).exists())
        self.assertEqual('2b64de742ad7425e777a40d32eac32dcb053749dd70ff56cb0a9037fc6181f5d', hashlib.sha256(MIGRATION.read_bytes()).hexdigest())
        self.assertEqual('executeInTransaction=true\n', (MANAGED / (name + '.conf')).read_text())
        self.assertIn('<target>12</target>', (ROOT / 'pom.xml').read_text())
        self.assertIn('<flyway.skip>true</flyway.skip>', (ROOT / 'pom.xml').read_text())

    def test_sql_does_not_mutate_existing_rows_or_remove_tables(self):
        sql = MIGRATION.read_text()
        without_comments = re.sub(r'--[^\n]*', '', sql)
        self.assertNotRegex(without_comments.upper(), r'\b(DELETE|TRUNCATE|INSERT|UPDATE)\s+(FROM|INTO|PUBLIC\.|USERS|NOTIFICATIONS)')
        self.assertNotRegex(without_comments.upper(), r'\bDROP\s+(TABLE|COLUMN|SCHEMA|DATABASE)\b')
        self.assertEqual({'club_comments', 'club_content_likes', 'club_comment_likes', 'club_comment_moderation_actions'},
                         set(re.findall(r'CREATE TABLE (\w+)', sql)))
        self.assertIn("server_version_num')::int NOT BETWEEN 170000 AND 179999", sql)
        self.assertIn("inet_server_port() IS DISTINCT FROM 5434", sql)
        self.assertIn("host(inet_server_addr()) IS DISTINCT FROM '127.0.0.1'", sql)

    def test_grants_are_confined_to_new_objects_and_the_intended_role(self):
        expected = {'club_comments', 'club_content_likes', 'club_comment_likes', 'club_comment_moderation_actions'}
        for target, role, database in [('test', 'campus_plus_test_runner', 'campus_plus_test'),
                                       ('main', 'campus_plus_user', 'campus_plus')]:
            path = ROOT / 'db/provisioning/test/09-club-interaction-runtime-grants.psql' if target == 'test' else ROOT / 'db/provisioning/main/06-club-interaction-runtime-grants.psql'
            text = path.read_text()
            grants = re.findall(r'^GRANT (.+?) ON (.+?) TO (\w+);$', text, re.M)
            self.assertEqual(3, len(grants))
            for permissions, objects, recipient in grants:
                self.assertEqual(role, recipient)
                self.assertNotIn('DELETE', permissions)
                self.assertNotIn('TRUNCATE', permissions)
                self.assertNotIn('ALL', permissions)
                self.assertNotIn('GRANT OPTION', text)
                objects = objects.removeprefix('SEQUENCE ')
                names = {name.strip().removeprefix('public.') for name in objects.split(',')}
                if 'USAGE' in permissions:
                    self.assertEqual({table + '_id_seq' for table in expected}, names)
                    self.assertEqual('USAGE,SELECT', permissions)
                elif 'UPDATE' in permissions:
                    self.assertEqual(expected - {'club_comment_moderation_actions'}, names)
                else:
                    self.assertEqual({'club_comment_moderation_actions'}, names)
                    self.assertEqual('SELECT,INSERT', permissions)
            self.assertIn("current_database()<>'" + database + "'", text)
