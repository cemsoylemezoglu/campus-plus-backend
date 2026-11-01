package com.campusplus.backend;

import com.campusplus.backend.entity.Club;
import com.campusplus.backend.repository.PostModerationRepository;
import com.campusplus.backend.security.JwtTestKeys;
import com.campusplus.backend.service.post.PostLifecycleService;
import com.campusplus.backend.support.PostgresTestDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Prepared for separately approved V009 + limited test grants. No migration/reset/cleanup. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresPostIT.DatabaseConfiguration.class)
class PostgresPostIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean PostModerationRepository audit;
    @MockitoSpyBean PostLifecycleService lifecycle;
    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.from(System::getenv).registerProperties(registry);
        String secret = JwtTestKeys.newSecret(); registry.add("JWT_SECRET", () -> secret);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add("server.address", () -> "127.0.0.1");
    }
    @TestConfiguration(proxyBeanMethods = false) static class DatabaseConfiguration {
        @Bean DataSource dataSource() { return PostgresTestDatabase.from(System::getenv).writableDataSource(); }
    }
    // Token never participates in record-generated toString or assertion messages.
    static final class Actor { final long id; final String token; Actor(long id, String token) { this.id = id; this.token = token; } }
    record Fixture(Actor admin, Actor owner, Actor first, Actor second, long university, long club) {}
    JdbcTemplate db() { return new JdbcTemplate(source); }
    String unique() { return UUID.randomUUID().toString().replace("-", ""); }
    HttpResponse<String> request(String method, String path, Actor actor, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json");
        if (actor != null) builder.header("Authorization", "Bearer " + actor.token);
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode response(int status, String method, String path, Actor actor, Object body) throws Exception {
        var response = request(method, path, actor, body); assertEquals(status, response.statusCode(), method + " " + path + " status");
        assertFalse(response.body().contains("passwordHash")); assertFalse(response.body().contains("password_hash"));
        return json.readTree(response.body());
    }
    Actor actor(boolean admin) throws Exception {
        String email = "post-it-" + unique() + "@example.test", password = UUID.randomUUID() + "!a9";
        long id = response(201, "POST", "/api/auth/register", null, Map.of("name", "Post Student", "email", email, "password", password)).path("id").asLong();
        if (admin) db().update("insert into public.user_roles(user_id,role) values(?,'ADMIN')", id);
        String token = response(200, "POST", "/api/auth/login", null, Map.of("email", email, "password", password)).path("accessToken").asText();
        assertFalse(token.isBlank()); return new Actor(id, token);
    }
    long university(Actor admin) throws Exception {
        return response(201, "POST", "/api/universities", admin, Map.of("code", "PI_" + unique().substring(0, 20),
                "name", "Post University", "city", "Ankara", "countryCode", "TR")).path("id").asLong();
    }
    void profile(Actor actor, long university) throws Exception { response(201, "POST", "/api/me/student-profile", actor, Map.of("universityId", university, "department", "Engineering")); }
    Fixture fixture() throws Exception {
        Actor admin = actor(true), owner = actor(false), first = actor(false), second = actor(false); long university = university(admin);
        for (Actor actor : List.of(owner, first, second)) profile(actor, university);
        var club = response(201, "POST", "/api/clubs", owner, Map.of("name", "Post Club", "slug", "pi-" + unique(), "description", "Post fixture"));
        long id = club.path("club").path("id").asLong();
        response(200, "POST", "/api/admin/clubs/" + id + "/approve", admin, Map.of("version", club.path("version").asLong()));
        return new Fixture(admin, owner, first, second, university, id);
    }
    long id(JsonNode managed) { return managed.path("content").path("post").path("id").asLong(); }
    long student(Actor actor) throws Exception { return id(response(201, "POST", "/api/me/posts", actor, Map.of("body", "Post body"))); }
    long club(Fixture f, Actor actor, String audience) throws Exception {
        return id(response(201, "POST", "/api/clubs/" + f.club + "/posts", actor, Map.of("body", "Club body", "audience", audience)));
    }
    String path(Fixture f, long id) { return "/api/clubs/" + f.club + "/posts/" + id; }
    JsonNode join(Fixture f, Actor actor) throws Exception {
        var membership = response(201, "POST", "/api/clubs/" + f.club + "/membership-requests", actor, Map.of());
        return response(200, "POST", "/api/clubs/" + f.club + "/memberships/" + membership.path("id").asLong() + "/approve", f.owner, Map.of("version", membership.path("version").asLong()));
    }
    List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return first.call(); });
            var b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown(); return List.of(a.get(45, TimeUnit.SECONDS), b.get(45, TimeUnit.SECONDS));
        }
    }
    List<Long> feedIds(Fixture f, Actor actor) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (var node : response(200, "GET", "/api/feed?clubId=" + f.club + "&size=100", actor, null).path("items")) ids.add(node.path("id").asLong());
        return ids;
    }

    @Test void studentLifecycleOwnershipAndHistoricalUniversityAreEnforced() throws Exception {
        var f = fixture(); long id = student(f.first);
        response(404, "GET", "/api/posts/" + id, f.first, null);
        response(404, "PATCH", "/api/me/posts/" + id, f.admin, Map.of("version", 0, "body", "No"));
        response(200, "POST", "/api/me/posts/" + id + "/publish", f.first, Map.of("version", 0));
        Instant published = db().queryForObject("select published_at from public.posts where id=?", java.sql.Timestamp.class, id).toInstant();
        response(200, "PATCH", "/api/me/posts/" + id, f.first, Map.of("version", 1, "body", "Edited"));
        assertEquals(published, db().queryForObject("select published_at from public.posts where id=?", java.sql.Timestamp.class, id).toInstant());
        long elsewhere = university(f.admin);
        response(200, "PUT", "/api/me/student-profile", f.first, Map.of("version", 0, "universityId", elsewhere, "department", "Engineering"));
        response(404, "GET", "/api/posts/" + id, f.first, null); response(200, "GET", "/api/posts/" + id, f.second, null);
        response(409, "PATCH", "/api/me/posts/" + id, f.first, Map.of("version", 2, "body", "Wrong university"));
        response(200, "POST", "/api/me/posts/" + id + "/remove", f.first, Map.of("version", 2, "reason", "Removed by owner"));
        response(404, "GET", "/api/posts/" + id, f.second, null);
        response(409, "POST", "/api/me/posts/" + id + "/publish", f.first, Map.of("version", 3));
    }
    @Test void feedAppliesMembershipUniversityAndLifecycleBeforeLimiting() throws Exception {
        var f = fixture(); long open = club(f, f.owner, "UNIVERSITY_STUDENTS"), members = club(f, f.owner, "CLUB_MEMBERS");
        for (long id : List.of(open, members)) response(200, "POST", path(f, id) + "/publish", f.owner, Map.of("version", 0));
        assertEquals(List.of(open), feedIds(f, f.first)); response(404, "GET", "/api/posts/" + members, f.first, null);
        var membership = join(f, f.first); assertEquals(List.of(members, open), feedIds(f, f.first));
        response(200, "POST", "/api/clubs/" + f.club + "/membership/leave", f.first, Map.of("version", membership.path("version").asLong()));
        assertEquals(List.of(open), feedIds(f, f.first));
        long other = university(f.admin); response(200, "PUT", "/api/me/student-profile", f.second, Map.of("version", 0, "universityId", other, "department", "Engineering"));
        assertTrue(feedIds(f, f.second).isEmpty()); response(404, "GET", "/api/posts/" + open, f.second, null);
        response(200, "POST", "/api/admin/clubs/" + f.club + "/suspend", f.admin, Map.of("version", 1, "reason", "Pause"));
        assertTrue(feedIds(f, f.first).isEmpty()); response(404, "GET", "/api/posts/" + open, f.first, null);
    }
    @Test void cursorUsesIdTieBreakerAndDoesNotIncludeNewerInsertions() throws Exception {
        var f = fixture(); List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) { long id = club(f, f.owner, "UNIVERSITY_STUDENTS"); ids.add(id); response(200, "POST", path(f, id) + "/publish", f.owner, Map.of("version", 0)); }
        Instant common = Instant.now().minusSeconds(10);
        // Only this test's new fixture. Keep created/publish/update ordering valid.
        for (long id : ids) db().update("update public.posts set created_at=?,published_at=?,updated_at=? where id=?",
                java.sql.Timestamp.from(common.minusSeconds(1)), java.sql.Timestamp.from(common), java.sql.Timestamp.from(common), id);
        String base = "/api/feed?clubId=" + f.club + "&size=1";
        var first = response(200, "GET", base, f.first, null); assertEquals(ids.getLast(), first.path("items").get(0).path("id").asLong());
        long newer = club(f, f.owner, "UNIVERSITY_STUDENTS"); response(200, "POST", path(f, newer) + "/publish", f.owner, Map.of("version", 0));
        var second = response(200, "GET", base + "&cursor=" + first.path("nextCursor").asText(), f.first, null);
        assertEquals(ids.get(1), second.path("items").get(0).path("id").asLong());
        var last = response(200, "GET", base + "&cursor=" + second.path("nextCursor").asText(), f.first, null);
        assertEquals(ids.getFirst(), last.path("items").get(0).path("id").asLong()); assertFalse(last.path("hasNext").asBoolean());
        response(400, "GET", "/api/feed?cursor=" + first.path("nextCursor").asText(), f.first, null);
    }
    @Test void moderationAuditIsPrivateAppendOnlyAndCannotResurrectRemoval() throws Exception {
        var f = fixture(); long id = student(f.first); response(200, "POST", "/api/me/posts/" + id + "/publish", f.first, Map.of("version", 0));
        response(403, "POST", "/api/admin/posts/" + id + "/hide", f.owner, Map.of("version", 1, "reason", "No"));
        response(200, "POST", "/api/admin/posts/" + id + "/hide", f.admin, Map.of("version", 1, "reason", "Internal reason"));
        response(404, "GET", "/api/posts/" + id, f.second, null);
        var managed = response(200, "GET", "/api/me/posts/" + id + "/manage", f.first, null); assertFalse(managed.toString().contains("Internal reason"));
        assertEquals(2, response(200, "GET", "/api/admin/posts/" + id + "/moderation-actions", f.admin, null).path("content").get(0).path("resultingVersion").asLong());
        response(200, "POST", "/api/admin/posts/" + id + "/restore", f.admin, Map.of("version", 2, "reason", "Reviewed"));
        response(200, "GET", "/api/posts/" + id, f.second, null);
        response(200, "POST", "/api/me/posts/" + id + "/remove", f.first, Map.of("version", 3, "reason", "Mine"));
        response(409, "POST", "/api/admin/posts/" + id + "/restore", f.admin, Map.of("version", 4, "reason", "No resurrection"));
        assertEquals(2, db().queryForObject("select count(*) from public.post_moderation_actions where post_id=?", Integer.class, id));
        assertFalse(db().queryForObject("select has_table_privilege(current_user,'public.post_moderation_actions','UPDATE') or "
                + "has_table_privilege(current_user,'public.post_moderation_actions','DELETE') or has_table_privilege(current_user,'public.posts','DELETE')", Boolean.class));
    }
    @Test void competingPublicationHasOneWinner() throws Exception {
        var f = fixture(); long id = club(f, f.owner, "UNIVERSITY_STUDENTS");
        var statuses = race(() -> request("POST", path(f, id) + "/publish", f.owner, Map.of("version", 0)).statusCode(),
                () -> request("POST", path(f, id) + "/publish", f.admin, Map.of("version", 0)).statusCode());
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(1, db().queryForObject("select version from public.posts where id=?", Long.class, id));
    }
    @Test void clubArchiveAndPublicationCannotLeaveLivePost() throws Exception {
        var f = fixture(); long id = club(f, f.owner, "UNIVERSITY_STUDENTS");
        var statuses = race(() -> request("POST", path(f, id) + "/publish", f.owner, Map.of("version", 0)).statusCode(),
                () -> request("POST", "/api/clubs/" + f.club + "/archive", f.owner, Map.of("version", 1, "reason", "Archive")).statusCode());
        assertEquals(200, statuses.get(1)); assertTrue(Set.of(200, 409).contains(statuses.getFirst()));
        assertEquals("REMOVED", db().queryForObject("select status from public.posts where id=?", String.class, id));
        assertTrue(feedIds(f, f.first).isEmpty());
    }
    @Test void profileTransferAndPublishSerializeWithoutMovingExistingPost() throws Exception {
        var f = fixture(); long other = university(f.admin), id = student(f.first);
        var statuses = race(() -> request("POST", "/api/me/posts/" + id + "/publish", f.first, Map.of("version", 0)).statusCode(),
                () -> request("PUT", "/api/me/student-profile", f.first, Map.of("version", 0, "universityId", other, "department", "New")).statusCode());
        assertEquals(200, statuses.get(1)); assertTrue(Set.of(200, 409).contains(statuses.getFirst()));
        assertEquals(f.university, db().queryForObject("select university_id from public.posts where id=?", Long.class, id));
        response(404, "GET", "/api/posts/" + id, f.first, null);
        response(409, "PATCH", "/api/me/posts/" + id, f.first, Map.of("version", statuses.getFirst() == 200 ? 1 : 0, "body", "No"));
    }
    @Test void managerDemotionAndPublishRespectCurrentLocalPermission() throws Exception {
        var f = fixture(); var membership = join(f, f.first); long mid = membership.path("id").asLong();
        var manager = response(200, "PATCH", "/api/clubs/" + f.club + "/memberships/" + mid + "/role", f.owner,
                Map.of("version", membership.path("version").asLong(), "role", "MANAGER"));
        long id = club(f, f.first, "UNIVERSITY_STUDENTS");
        var statuses = race(() -> request("POST", path(f, id) + "/publish", f.first, Map.of("version", 0)).statusCode(),
                () -> request("PATCH", "/api/clubs/" + f.club + "/memberships/" + mid + "/role", f.owner,
                        Map.of("version", manager.path("version").asLong(), "role", "MEMBER")).statusCode());
        assertEquals(200, statuses.get(1)); assertTrue(Set.of(200, 403).contains(statuses.getFirst()));
        response(403, "PATCH", path(f, id), f.first, Map.of("version", statuses.getFirst() == 200 ? 1 : 0, "body", "Former creator"));
        db().update("insert into public.user_roles(user_id,role) values(?,'CLUB_ADMIN') on conflict do nothing", f.second.id);
        response(403, "POST", "/api/clubs/" + f.club + "/posts", f.second, Map.of("body", "Marker only"));
    }
    @Test void updateAndHideWithSameVersionHaveOneWinner() throws Exception {
        var f = fixture(); long id = student(f.first); response(200, "POST", "/api/me/posts/" + id + "/publish", f.first, Map.of("version", 0));
        var statuses = race(() -> request("PATCH", "/api/me/posts/" + id, f.first, Map.of("version", 1, "body", "Edited")).statusCode(),
                () -> request("POST", "/api/admin/posts/" + id + "/hide", f.admin, Map.of("version", 1, "reason", "Moderated")).statusCode());
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(2, db().queryForObject("select version from public.posts where id=?", Long.class, id));
    }
    @Test void auditFailureRollsBackFlushedSource() throws Exception {
        var f = fixture(); long id = student(f.first);
        doThrow(new IllegalStateException("Controlled post audit rollback")).when(audit).save(any());
        try { response(500, "POST", "/api/admin/posts/" + id + "/hide", f.admin, Map.of("version", 0, "reason", "Test only")); }
        finally { reset(audit); }
        assertFalse(db().queryForObject("select moderation_hidden from public.posts where id=?", Boolean.class, id));
        assertEquals(0, db().queryForObject("select version from public.posts where id=?", Long.class, id));
        assertEquals(0, db().queryForObject("select count(*) from public.post_moderation_actions where post_id=?", Integer.class, id));
    }
    @Test void postFailureRollsBackEntireClubArchive() throws Exception {
        var f = fixture(); long id = club(f, f.owner, "UNIVERSITY_STUDENTS");
        PostLifecycleService target = AopTestUtils.getUltimateTargetObject(lifecycle);
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("Controlled post archive rollback"); })
                .when(target).clubArchived(any(Club.class), any(), any());
        try { response(500, "POST", "/api/clubs/" + f.club + "/archive", f.owner, Map.of("version", 1, "reason", "Test only")); }
        finally { reset(target); }
        assertEquals("ACTIVE", db().queryForObject("select status from public.clubs where id=?", String.class, f.club));
        assertEquals("DRAFT", db().queryForObject("select status from public.posts where id=?", String.class, id));
    }
    void rejectedSql(String state, String sql, Object... args) throws Exception {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                assertEquals(state, assertThrows(SQLException.class, statement::executeUpdate).getSQLState());
            } finally { connection.rollback(); }
        }
    }
    @Test void databaseConstraintsAndRuntimeAuditPrivilegesAreReal() throws Exception {
        var f = fixture(); long id = club(f, f.owner, "UNIVERSITY_STUDENTS"), other = university(f.admin);
        rejectedSql("23503", "update public.posts set university_id=? where id=?", other, id);
        rejectedSql("23514", "update public.posts set publisher='STUDENT' where id=?", id);
        rejectedSql("23514", "update public.posts set body=E' \t\n' where id=?", id);
        rejectedSql("23514", "update public.posts set status='PUBLISHED' where id=?", id);
        rejectedSql("23514", "update public.posts set version=-1 where id=?", id);
        rejectedSql("42501", "update public.post_moderation_actions set reason='forbidden' where false");
        response(200, "POST", "/api/admin/posts/" + id + "/hide", f.admin, Map.of("version", 0, "reason", "Audit fixture"));
        rejectedSql("23505", "insert into public.post_moderation_actions(post_id,actor_id,action,reason,created_at,resulting_version) values(?,?,'HIDE','Duplicate',now(),1)", id, f.admin.id);
    }
    @Test void scalarFeedDoesNotLoadOneUserRoleCollectionPerPost() throws Exception {
        var f = fixture();
        db().update("insert into public.posts(university_id,created_by,publisher,body,audience,status,published_at,created_at,updated_at,version) "
                + "select ?,?,'STUDENT','Paging fixture '||s,'UNIVERSITY_STUDENTS','PUBLISHED',now(),now(),now(),0 from generate_series(1,100) s", f.university, f.first.id);
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics(); statistics.clear();
        var feed = response(200, "GET", "/api/feed?size=100", f.second, null);
        assertEquals(100, feed.path("items").size()); assertFalse(feed.path("hasNext").asBoolean());
        assertTrue(statistics.getPrepareStatementCount() <= 12, "Feed statement count must stay bounded independently of post count");
    }
}
