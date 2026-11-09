package com.campusplus.backend;

import com.campusplus.backend.dto.messaging.*;
import com.campusplus.backend.security.JwtTestKeys;
import com.campusplus.backend.service.messaging.DirectMessageService;
import com.campusplus.backend.support.PostgresTestDatabase;
import java.net.URI;
import java.net.http.*;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** PREPARED ONLY: needs separately approved V012 + limited test grants. No DDL/migration/cleanup. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresMessagingIT.DatabaseConfiguration.class)
class PostgresMessagingIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DirectMessageService service;
    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.from(System::getenv).registerProperties(registry);
        String secret = JwtTestKeys.newSecret(); registry.add("JWT_SECRET", () -> secret);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false"); registry.add("server.address", () -> "127.0.0.1");
    }
    @TestConfiguration(proxyBeanMethods = false) static class DatabaseConfiguration {
        @Bean DataSource dataSource() { return new CountingSource(PostgresTestDatabase.from(System::getenv).writableDataSource()); }
    }
    // Includes both Hibernate and JdbcTemplate prepares; excludes delegate's target-safety probe.
    static final class CountingSource extends AbstractDataSource {
        final DataSource delegate; final AtomicLong statements = new AtomicLong();
        CountingSource(DataSource delegate) { this.delegate = delegate; }
        @Override public Connection getConnection() throws SQLException { return wrap(delegate.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException { return wrap(delegate.getConnection(user,password)); }
        private Connection wrap(Connection connection) {
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
                if (method.getName().equals("prepareStatement") || method.getName().equals("prepareCall")) statements.incrementAndGet();
                try { return method.invoke(connection,args); } catch (InvocationTargetException ex) { throw ex.getCause(); }
            });
        }
    }
    static final class Actor {
        final long id; final String token;
        Actor(long id, String token) { this.id = id; this.token = token; }
    }
    record Fixture(Actor first, Actor second, Actor admin, long university) {}
    JdbcTemplate db() { return new JdbcTemplate(source); }
    HttpResponse<String> request(String method, String path, Actor actor, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json");
        if (actor != null) builder.header("Authorization", "Bearer " + actor.token);
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode response(int status, String method, String path, Actor actor, Object body) throws Exception {
        var result = request(method, path, actor, body); assertEquals(status, result.statusCode(), method + " " + path);
        assertFalse(result.body().contains("passwordHash")); assertFalse(result.body().contains("password_hash"));
        if (status == 200 || status == 204) assertTrue(result.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        return result.body().isEmpty() ? json.createObjectNode() : json.readTree(result.body());
    }
    Actor actor(boolean admin) throws Exception {
        String email = "messaging-it-" + UUID.randomUUID() + "@example.test", password = UUID.randomUUID() + "!a9";
        long id = response(201, "POST", "/api/auth/register", null, Map.of("name", "Messaging Student", "email", email, "password", password)).path("id").asLong();
        if (admin) db().update("insert into public.user_roles(user_id,role) values(?,'ADMIN')", id);
        String token = response(200, "POST", "/api/auth/login", null, Map.of("email", email, "password", password)).path("accessToken").asText();
        assertFalse(token.isBlank()); return new Actor(id, token);
    }
    Fixture fixture() throws Exception {
        Actor first = actor(false), second = actor(false), admin = actor(true);
        long uni = response(201, "POST", "/api/universities", admin, Map.of("code", "DM_" + UUID.randomUUID().toString().substring(0,20),
                "name", "Messaging University", "city", "Ankara", "countryCode", "TR")).path("id").asLong();
        for (Actor actor : List.of(first, second)) response(201, "POST", "/api/me/student-profile", actor, Map.of("universityId", uni, "department", "Engineering"));
        return new Fixture(first, second, admin, uni);
    }
    Map<String,Object> message(UUID key, String body) { return Map.of("clientMessageId", key.toString(), "body", body); }
    JsonNode start(Actor sender, Actor recipient, UUID key, String text) throws Exception {
        return response(200, "POST", "/api/me/conversations", sender, Map.of("recipientUserId", recipient.id, "message", message(key, text)));
    }
    String path(long id) { return "/api/me/conversations/" + id; }
    @Test void realDeliveryInboxUnreadReadAndPrivacy() throws Exception {
        var f = fixture(); var first = start(f.first, f.second, UUID.randomUUID(), "Private message"); long id = first.path("conversationId").asLong();
        var history = response(200, "GET", path(id) + "/messages", f.second, null);
        assertEquals("Private message", history.path("content").get(0).path("body").asText());
        assertFalse(history.toString().contains("clientMessageId")); assertFalse(history.toString().contains("email"));
        assertEquals(1, response(200, "GET", "/api/me/conversations/unread-count", f.second, null).path("count").asLong());
        assertEquals(0, response(200, "GET", "/api/me/conversations/unread-count", f.first, null).path("count").asLong());
        assertEquals(1, response(200, "GET", "/api/me/conversations", f.second, null).path("content").size());
        response(200, "PUT", path(id) + "/read", f.second, Map.of("throughMessageNumber", 1));
        assertEquals(0, response(200, "GET", "/api/me/conversations/unread-count", f.second, null).path("count").asLong());
    }
    @Test void administratorsCannotReadSendOrMarkOthersPrivateMessages() throws Exception {
        var f = fixture(); long id = start(f.first, f.second, UUID.randomUUID(), "secret").path("conversationId").asLong();
        response(404, "GET", path(id) + "/messages", f.admin, null);
        response(404, "POST", path(id) + "/messages", f.admin, message(UUID.randomUUID(), "intrusion"));
        response(404, "PUT", path(id) + "/read", f.admin, Map.of("throughMessageNumber", 1));
        assertEquals(0, response(200, "GET", "/api/me/conversations", f.admin, null).path("content").size());
        response(401, "GET", path(id) + "/messages", null, null);
    }
    @Test void retriesAreIdempotentAndChangedBodyConflicts() throws Exception {
        var f = fixture(); UUID key = UUID.randomUUID(); var first = start(f.first, f.second, key, "hello");
        var again = start(f.first, f.second, key, "hello"); assertEquals(first.path("id"), again.path("id"));
        long id = first.path("conversationId").asLong();
        response(409, "POST", path(id) + "/messages", f.first, message(key, "changed"));
        assertEquals(1, db().queryForObject("select count(*) from public.direct_messages where conversation_id=?", Long.class, id));
        assertEquals(1, db().queryForObject("select last_message_number from public.direct_conversations where id=?", Long.class, id));
    }
    @Test void concurrentFirstMessagesCreateOneConversationAndTwoStates() throws Exception {
        var f = fixture(); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); return start(f.first, f.second, UUID.randomUUID(), "one"); });
            var b = pool.submit(() -> { gate.await(); return start(f.second, f.first, UUID.randomUUID(), "two"); }); gate.countDown();
            var first = a.get(45, TimeUnit.SECONDS); var second = b.get(45, TimeUnit.SECONDS);
            assertEquals(first.path("conversationId"), second.path("conversationId")); long id = first.path("conversationId").asLong();
            assertEquals(Set.of(1L,2L), Set.of(first.path("messageNumber").asLong(), second.path("messageNumber").asLong()));
            assertEquals(2, db().queryForObject("select count(*) from public.direct_conversation_states where conversation_id=?", Long.class, id));
        }
    }
    @Test void concurrentRetriesCreateOneMessage() throws Exception {
        var f = fixture(); long id = start(f.first, f.second, UUID.randomUUID(), "initial").path("conversationId").asLong();
        UUID key = UUID.randomUUID(); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); return response(200,"POST",path(id)+"/messages",f.first,message(key,"retry")); });
            var b = pool.submit(() -> { gate.await(); return response(200,"POST",path(id)+"/messages",f.first,message(key,"retry")); }); gate.countDown();
            assertEquals(a.get(45,TimeUnit.SECONDS).path("id"), b.get(45,TimeUnit.SECONDS).path("id"));
        }
        assertEquals(2, db().queryForObject("select count(*) from public.direct_messages where conversation_id=?", Long.class, id));
    }
    @Test void blockingStopsBothDirectionsPreservesHistoryAndCanBeRemoved() throws Exception {
        var f = fixture(); long id = start(f.first, f.second, UUID.randomUUID(), "initial").path("conversationId").asLong();
        response(204,"PUT","/api/me/blocks/"+f.first.id,f.second,null);
        for (Actor actor : List.of(f.first,f.second)) {
            response(404,"POST",path(id)+"/messages",actor,message(UUID.randomUUID(),"blocked"));
            response(200,"GET",path(id)+"/messages",actor,null);
        }
        response(200,"PUT",path(id)+"/read",f.second,Map.of("throughMessageNumber",1));
        assertEquals(1,response(200,"GET","/api/me/blocks",f.second,null).path("content").size());
        assertEquals(0,response(200,"GET","/api/me/blocks",f.first,null).path("content").size());
        response(204,"DELETE","/api/me/blocks/"+f.first.id,f.second,null);
        response(200,"POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),"unblocked"));
    }
    @Test void concurrentBlockAndSendLinearizeAndLaterSendAlwaysFails() throws Exception {
        var f = fixture(); long id = start(f.first, f.second, UUID.randomUUID(), "initial").path("conversationId").asLong();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var send = pool.submit(() -> { gate.await(); return request("POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),"racing")); });
            var block = pool.submit(() -> { gate.await(); return request("PUT","/api/me/blocks/"+f.first.id,f.second,null); }); gate.countDown();
            assertTrue(Set.of(200,404).contains(send.get(45,TimeUnit.SECONDS).statusCode())); assertEquals(204,block.get(45,TimeUnit.SECONDS).statusCode());
        }
        response(404,"POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),"later"));
    }
    @Test void missingAndForeignProfilesAndInactiveUniversityCannotReceiveNewMessages() throws Exception {
        var f = fixture();
        response(404,"POST","/api/me/conversations",f.first,Map.of("recipientUserId",f.admin.id,"message",message(UUID.randomUUID(),"no profile")));
        long foreign = response(201,"POST","/api/universities",f.admin,Map.of("code","DM_"+UUID.randomUUID().toString().substring(0,20),"name","Other University","city","Izmir","countryCode","TR")).path("id").asLong();
        response(201,"POST","/api/me/student-profile",f.admin,Map.of("universityId",foreign,"department","Engineering"));
        response(404,"POST","/api/me/conversations",f.first,Map.of("recipientUserId",f.admin.id,"message",message(UUID.randomUUID(),"foreign")));
        long id = start(f.first,f.second,UUID.randomUUID(),"before deactivation").path("conversationId").asLong();
        db().update("update public.universities set active=false,version=version+1 where id=?",f.university);
        response(404,"POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),"inactive")); response(200,"GET",path(id)+"/messages",f.second,null);
    }
    @Test void paginationIsConversationBoundAndReadStateCannotRegressOrJumpAhead() throws Exception {
        var f = fixture(); long id = start(f.first,f.second,UUID.randomUUID(),"one").path("conversationId").asLong();
        for (String text : List.of("two","three")) response(200,"POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),text));
        var first = response(200,"GET",path(id)+"/messages?limit=2",f.second,null); assertEquals(2,first.path("content").size());
        String cursor = first.path("nextCursor").asText();
        var next = response(200,"GET",path(id)+"/messages?limit=2&cursor="+cursor,f.second,null); assertEquals(1,next.path("content").size());
        response(400,"GET",path(id)+"/messages?cursor="+cursor,f.first,null);
        response(200,"PUT",path(id)+"/read",f.second,Map.of("throughMessageNumber",3));
        assertEquals(3,response(200,"PUT",path(id)+"/read",f.second,Map.of("throughMessageNumber",1)).path("throughMessageNumber").asLong());
        response(400,"PUT",path(id)+"/read",f.second,Map.of("throughMessageNumber",4));
    }
    @Test void databaseRejectsForgedParticipantsAndGrantsAreNarrow() throws Exception {
        var f = fixture(); long id = start(f.first,f.second,UUID.randomUUID(),"initial").path("conversationId").asLong();
        long lower = Math.min(f.first.id,f.second.id), higher = Math.max(f.first.id,f.second.id);
        var tx = new TransactionTemplate(transactions);
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(s -> db().update(
                "insert into public.direct_messages(conversation_id,lower_user_id,higher_user_id,sender_id,message_number,client_message_id,body,created_at) values(?,?,?,?,99,?,'forged',now())",
                id,lower,higher,f.admin.id,UUID.randomUUID())));
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(s -> db().update(
                "insert into public.direct_conversation_states(conversation_id,lower_user_id,higher_user_id,user_id,updated_at) values(?,?,?,?,now())",id,lower,higher,f.admin.id)));
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(s -> db().update(
                "insert into public.direct_conversation_states(conversation_id,lower_user_id,higher_user_id,user_id,updated_at) values(?,?,?,?,now())",id,lower,f.admin.id,lower)));
        assertFalse(Boolean.TRUE.equals(db().queryForObject("select has_table_privilege(current_user,'public.direct_messages','UPDATE')",Boolean.class)));
        for (String table : List.of("direct_messages","direct_conversations","direct_conversation_states","user_blocks"))
            assertFalse(Boolean.TRUE.equals(db().queryForObject("select has_table_privilege(current_user,?,'DELETE')",Boolean.class,"public."+table)));
    }
    @Test void transactionRollbackLeavesNoConversationMessageOrState() throws Exception {
        var f = fixture(); var tx = new TransactionTemplate(transactions);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(Long.toString(f.first.id),null,List.of()));
        try {
            assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                service.start(new StartDirectConversationRequest(f.second.id,new SendDirectMessageRequest(UUID.randomUUID(),"rolled back")));
                throw new IllegalStateException("controlled rollback");
            }));
        } finally { SecurityContextHolder.clearContext(); }
        assertEquals(0,db().queryForObject("select count(*) from public.direct_conversations where lower_user_id=? and higher_user_id=?",Long.class,Math.min(f.first.id,f.second.id),Math.max(f.first.id,f.second.id)));
        assertEquals(0,db().queryForObject("select count(*) from public.direct_messages where sender_id=?",Long.class,f.first.id));
    }
    @Test void hundredMessagePageHasBoundedSqlIncludingJdbcAndHibernate() throws Exception {
        var f = fixture(); long id = start(f.first,f.second,UUID.randomUUID(),"first").path("conversationId").asLong();
        long lower = Math.min(f.first.id,f.second.id), higher = Math.max(f.first.id,f.second.id);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            db().update("insert into public.direct_messages(conversation_id,lower_user_id,higher_user_id,sender_id,message_number,client_message_id,body,created_at) "
                    + "select ?,?,?,?,n,gen_random_uuid(),'Page fixture',now() from generate_series(2,101) as n",id,lower,higher,f.first.id);
            db().update("update public.direct_conversations set last_message_number=101,last_message_at=greatest(last_message_at,now()),version=version+1 where id=?",id);
        });
        var counter = ((CountingSource)source).statements; long before = counter.get();
        var page = response(200,"GET",path(id)+"/messages?limit=100",f.second,null); long count = counter.get()-before;
        assertEquals(100,page.path("content").size()); assertFalse(page.path("nextCursor").isNull());
        assertTrue(count>0 && count<=15,"Bounded prepared SQL, including JdbcTemplate projections");
    }
    @Test void concurrentReadReceiptsNeverMoveBackwards() throws Exception {
        var f = fixture(); long id = start(f.first,f.second,UUID.randomUUID(),"one").path("conversationId").asLong();
        response(200,"POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),"two"));
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); return response(200,"PUT",path(id)+"/read",f.second,Map.of("throughMessageNumber",1)); });
            var b = pool.submit(() -> { gate.await(); return response(200,"PUT",path(id)+"/read",f.second,Map.of("throughMessageNumber",2)); }); gate.countDown();
            a.get(45,TimeUnit.SECONDS); b.get(45,TimeUnit.SECONDS);
        }
        assertEquals(2,db().queryForObject("select last_read_message_number from public.direct_conversation_states where conversation_id=? and user_id=?",Long.class,id,f.second.id));
    }
    @Test void profileChangeHoldingUserLockIsRecheckedBeforeSending() throws Exception {
        var f = fixture(); long id = start(f.first,f.second,UUID.randomUUID(),"initial").path("conversationId").asLong();
        long foreign = response(201,"POST","/api/universities",f.admin,Map.of("code","DM_"+UUID.randomUUID().toString().substring(0,20),"name","Transfer University","city","Izmir","countryCode","TR")).path("id").asLong();
        var changed = new CountDownLatch(1); var commit = new CountDownLatch(1); var sendStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var update = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                db().queryForObject("select id from public.users where id=? for no key update",Long.class,f.first.id);
                db().update("update public.student_profiles set university_id=?,version=version+1 where user_id=?",foreign,f.first.id);
                changed.countDown();
                try { if (!commit.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Commit barrier timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            assertTrue(changed.await(10,TimeUnit.SECONDS));
            var send = pool.submit(() -> { sendStarted.countDown(); return request("POST",path(id)+"/messages",f.first,message(UUID.randomUUID(),"after transfer")); });
            assertTrue(sendStarted.await(10,TimeUnit.SECONDS)); commit.countDown(); update.get(20,TimeUnit.SECONDS);
            assertEquals(404,send.get(45,TimeUnit.SECONDS).statusCode());
        } finally { commit.countDown(); }
        assertEquals(1,db().queryForObject("select count(*) from public.direct_messages where conversation_id=?",Long.class,id));
    }
}
