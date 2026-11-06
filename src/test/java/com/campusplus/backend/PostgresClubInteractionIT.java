package com.campusplus.backend;

import com.campusplus.backend.entity.*;
import java.sql.Connection;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.campusplus.backend.repository.ClubCommentModerationRepository;
import com.campusplus.backend.service.notification.ClubCommentNotificationWriter;
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

/** Prepared for separately approved V011 + limited test grants. No migration/reset/cleanup. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresClubInteractionIT.DatabaseConfiguration.class)
class PostgresClubInteractionIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean ClubCommentModerationRepository commentAudit;
    @MockitoSpyBean ClubCommentNotificationWriter socialWriter;
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
        String email = "comment-it-" + unique() + "@example.test", password = UUID.randomUUID() + "!a9";
        long id = response(201, "POST", "/api/auth/register", null, Map.of("name", "Comment Student", "email", email, "password", password)).path("id").asLong();
        if (admin) db().update("insert into public.user_roles(user_id,role) values(?,'ADMIN')", id);
        String token = response(200, "POST", "/api/auth/login", null, Map.of("email", email, "password", password)).path("accessToken").asText();
        assertFalse(token.isBlank()); return new Actor(id, token);
    }
    long university(Actor admin) throws Exception {
        return response(201, "POST", "/api/universities", admin, Map.of("code", "CI_" + unique().substring(0, 20),
                "name", "Comment University", "city", "Ankara", "countryCode", "TR")).path("id").asLong();
    }
    void profile(Actor actor, long university) throws Exception { response(201, "POST", "/api/me/student-profile", actor, Map.of("universityId", university, "department", "Engineering")); }
    Fixture fixture() throws Exception {
        Actor admin = actor(true), owner = actor(false), first = actor(false), second = actor(false); long university = university(admin);
        for (Actor actor : List.of(owner, first, second)) profile(actor, university);
        var club = response(201, "POST", "/api/clubs", owner, Map.of("name", "Comment Club", "slug", "pi-" + unique(), "description", "Post fixture"));
        long id = club.path("club").path("id").asLong();
        response(200, "POST", "/api/admin/clubs/" + id + "/approve", admin, Map.of("version", club.path("version").asLong()));
        return new Fixture(admin, owner, first, second, university, id);
    }
    record Content(boolean event,long id,long club) {
        String path(){return "/api/"+(event?"events":"announcements")+"/"+id;}
        String managed(){return "/api/clubs/"+club+"/"+(event?"events":"announcements")+"/"+id;}
        String column(){return event?"event_id":"announcement_id";}
    }
    Content content(Fixture f,boolean event,String audience)throws Exception {
        JsonNode created;
        if(event){Instant now=Instant.now();created=response(201,"POST","/api/clubs/"+f.club+"/events",f.owner,
            Map.of("title","Interaction event","description","Description","venue","Hall","audience",audience,"capacity",10,
                "registrationOpensAt",now.minusSeconds(60).toString(),"registrationClosesAt",now.plusSeconds(1800).toString(),
                "startsAt",now.plusSeconds(3600).toString(),"endsAt",now.plusSeconds(7200).toString(),"timeZone","Europe/Istanbul"));}
        else created=response(201,"POST","/api/clubs/"+f.club+"/announcements",f.owner,Map.of("title","Interaction announcement","body","Body","audience",audience));
        var value=created.path(event?"event":"announcement");var c=new Content(event,value.path("id").asLong(),f.club);
        response(200,"POST",c.managed()+"/publish",f.owner,Map.of("version",value.path("version").asLong()));return c;
    }
    long create(Content c,Actor actor)throws Exception{return response(201,"POST",c.path()+"/comments",actor,Map.of("body","Comment body")).path("id").asLong();}
    long count(String table,Content c){return db().queryForObject("select count(*) from public."+table+" where "+c.column()+"=?",Long.class,c.id);}
    long version(long comment){return db().queryForObject("select version from public.club_comments where id=?",Long.class,comment);}
    List<Integer> race(Callable<Integer> first,Callable<Integer> second)throws Exception {
        var start=new CountDownLatch(1);try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->{start.await();return first.call();});var b=executor.submit(()->{start.await();return second.call();});
            start.countDown();return List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));}
    }
    JsonNode join(Fixture f,Actor actor)throws Exception {
        var m=response(201,"POST","/api/clubs/"+f.club+"/membership-requests",actor,Map.of());
        return response(200,"POST","/api/clubs/"+f.club+"/memberships/"+m.path("id").asLong()+"/approve",f.owner,Map.of("version",m.path("version").asLong()));
    }
    @Test void sqlPaginationPlaceholderAndSourceBoundCursors()throws Exception {
        var f=fixture();for(boolean event:List.of(true,false)){
            var c=content(f,event,"UNIVERSITY_STUDENTS");long root=create(c,f.first);create(c,f.second);
            response(201,"POST",c.path()+"/comments/"+root+"/replies",f.second,Map.of("body","Reply"));
            response(200,"POST","/api/me/club-comments/"+root+"/remove",f.first,Map.of("version",0));
            var page=response(200,"GET",c.path()+"/comments?size=1",f.second,null);
            assertEquals(root,page.path("items").get(0).path("id").asLong());assertTrue(page.path("items").get(0).path("body").isNull());
            assertTrue(page.path("items").get(0).path("author").isNull());assertEquals(1,page.path("items").get(0).path("replyCount").asLong());
            String cursor=page.path("nextCursor").asText();assertFalse(cursor.isBlank());
            assertEquals(1,response(200,"GET",c.path()+"/comments?size=1&cursor="+cursor,f.second,null).path("items").size());
            var other=content(f,!event,"UNIVERSITY_STUDENTS");response(400,"GET",other.path()+"/comments?cursor="+cursor,f.first,null);
        }
    }
    @Test void notificationRecipientsPriorityAndDynamicHiddenTargets()throws Exception {
        var f=fixture();for(boolean event:List.of(true,false)){
            var c=content(f,event,"UNIVERSITY_STUDENTS");long root=create(c,f.owner);
            long reply=response(201,"POST",c.path()+"/comments/"+root+"/replies",f.first,Map.of("body","Reply")).path("id").asLong();
            assertEquals(1,db().queryForObject("select count(*) from public.notifications where recipient_id=? and source_key=?",Long.class,f.owner.id,"CLUB_COMMENT_CREATED:"+reply));
            assertEquals((event?"EVENT":"ANNOUNCEMENT")+"_COMMENT_REPLIED",db().queryForObject("select type from public.notifications where recipient_id=? and source_key=?",String.class,f.owner.id,"CLUB_COMMENT_CREATED:"+reply));
            response(200,"POST","/api/admin/club-comments/"+root+"/hide",f.admin,Map.of("version",0,"reason","Hide thread"));
            long notice=db().queryForObject("select id from public.notifications where recipient_id=? and source_key=?",Long.class,f.owner.id,"CLUB_COMMENT_CREATED:"+reply);
            var page=response(200,"GET","/api/me/notifications?size=100",f.owner,null);
            var inbox=java.util.stream.StreamSupport.stream(page.path("content").spliterator(),false)
                .filter(row->row.path("id").asLong()==notice).findFirst().orElseThrow();
            assertFalse(inbox.path("available").asBoolean());assertTrue(inbox.path("target").isNull());
        }
    }
    @Test void concurrentLikeRetriesAreIdempotentAndDoNotBumpSourceVersion()throws Exception {
        var f=fixture();for(boolean event:List.of(true,false)){
            var c=content(f,event,"UNIVERSITY_STUDENTS");long id=create(c,f.owner);
            var statuses=race(()->request("PUT",c.path()+"/like",f.first,null).statusCode(),()->request("PUT",c.path()+"/like",f.first,null).statusCode());
            assertEquals(List.of(200,200),statuses);assertEquals(1,count("club_content_likes",c));
            var likes=race(()->request("PUT",c.path()+"/comments/"+id+"/like",f.first,null).statusCode(),()->request("PUT",c.path()+"/comments/"+id+"/like",f.first,null).statusCode());
            assertEquals(List.of(200,200),likes);assertEquals(1,db().queryForObject("select count(*) from public.club_comment_likes where comment_id=?",Long.class,id));
            response(204,"DELETE",c.path()+"/like",f.first,null);response(200,"PUT",c.path()+"/like",f.first,null);
            assertEquals(1,db().queryForObject("select version from public."+(event?"events":"club_announcements")+" where id=?",Long.class,c.id));
            assertEquals(1,db().queryForObject("select count(*) from public.notifications where recipient_id=? and "+c.column()+"=? and type=?",Long.class,f.owner.id,c.id,(event?"EVENT":"ANNOUNCEMENT")+"_LIKED"));
        }
    }
    @Test void terminalRootRemovalAndReplyRaceRemainConsistent()throws Exception {
        var f=fixture();for(boolean event:List.of(true,false)){
            var c=content(f,event,"UNIVERSITY_STUDENTS");long root=create(c,f.first);
            var statuses=race(()->request("POST","/api/me/club-comments/"+root+"/remove",f.first,Map.of("version",0)).statusCode(),
                ()->request("POST",c.path()+"/comments/"+root+"/replies",f.second,Map.of("body","Racing reply")).statusCode());
            assertEquals(200,statuses.get(0));assertTrue(Set.of(201,404).contains(statuses.get(1)));
            assertEquals("REMOVED",db().queryForObject("select status from public.club_comments where id=?",String.class,root));
            response(404,"POST",c.path()+"/comments/"+root+"/replies",f.second,Map.of("body","Late reply"));
        }
    }
    @Test void sourceLifecycleAndCommentRaceUseCompatibleLocks()throws Exception {
        var f=fixture();for(boolean event:List.of(true,false)){
            var c=content(f,event,"UNIVERSITY_STUDENTS");
            var statuses=race(()->request("POST",c.path()+"/comments",f.first,Map.of("body","Racing comment")).statusCode(),
                ()->request("POST",c.managed()+"/"+(event?"cancel":"withdraw"),f.owner,Map.of("version",1,"reason","Close source")).statusCode());
            assertEquals(200,statuses.get(1));assertTrue((event?Set.of(201,403):Set.of(201,404)).contains(statuses.get(0)));
            response(event?403:404,"POST",c.path()+"/comments",f.first,Map.of("body","Too late"));
        }
    }
    @Test void privateMembershipLossKeepsOnlyOwnCleanupAvailable()throws Exception {
        var f=fixture();var membership=join(f,f.first);
        var event=content(f,true,"CLUB_MEMBERS");var announcement=content(f,false,"CLUB_MEMBERS");
        long e=create(event,f.first),a=create(announcement,f.first);
        for(var c:List.of(event,announcement))response(200,"PUT",c.path()+"/like",f.first,null);
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",membership.path("version").asLong()));
        for(var c:List.of(event,announcement)){
            response(404,"GET",c.path()+"/comments",f.first,null);response(204,"DELETE",c.path()+"/like",f.first,null);
            response(200,"POST","/api/me/club-comments/"+(c.event?e:a)+"/remove",f.first,Map.of("version",0));
        }
    }
    @Test void foreignUniversityReadWriteAndAdminDraftBoundaries()throws Exception {
        var f=fixture();long foreign=university(f.admin);
        response(200,"PUT","/api/me/student-profile",f.second,Map.of("version",0,"universityId",foreign,"department","Engineering"));
        var e=content(f,true,"UNIVERSITY_STUDENTS");var a=content(f,false,"UNIVERSITY_STUDENTS");
        response(200,"GET",e.path()+"/comments",f.second,null);response(403,"POST",e.path()+"/comments",f.second,Map.of("body","Foreign"));
        response(404,"GET",a.path()+"/comments",f.second,null);
        long id=create(e,f.first);response(403,"POST",e.path()+"/comments/"+id+"/hide",f.admin,Map.of("version",0,"reason","Not local owner"));
    }
    @Test void writerFailureAfterInboxInsertRollsBackCommentAndNotice()throws Exception {
        var f=fixture();var c=content(f,true,"UNIVERSITY_STUDENTS");
        ClubCommentNotificationWriter target=AopTestUtils.getUltimateTargetObject(socialWriter);
        var afterInsert=new java.util.concurrent.atomic.AtomicBoolean();
        try{
            doAnswer(invocation->{invocation.callRealMethod();afterInsert.set(true);throw new IllegalStateException("Injected test writer failure");}).when(target).created(any(),any());
            response(500,"POST",c.path()+"/comments",f.first,Map.of("body","Must rollback"));
        }finally{reset(target);}
        assertTrue(afterInsert.get(),"Real inbox writer must execute before the injected failure");
        assertEquals(0,count("club_comments",c));
        assertEquals(0,db().queryForObject("select count(*) from public.notifications where event_id=? and club_comment_id is not null",Long.class,c.id));
    }
    @Test void auditFailureRollsBackModerationFlagAndVersion()throws Exception {
        var f=fixture();var c=content(f,false,"UNIVERSITY_STUDENTS");long id=create(c,f.first);
        var afterAuditInsert=new java.util.concurrent.atomic.AtomicBoolean();
        try{
            // Repository spies wrap a JDK proxy: callRealMethod cannot invoke its abstract flush.
            // IDENTITY save has already inserted the audit row. Observe it on this transaction's
            // JDBC connection before injecting failure, then prove both writes disappear below.
            doAnswer(invocation->{
                assertEquals(1,db().queryForObject("select count(*) from public.club_comment_moderation_actions where comment_id=?",Long.class,id));
                assertTrue(db().queryForObject("select resource_hidden from public.club_comments where id=?",Boolean.class,id));
                afterAuditInsert.set(true);throw new IllegalStateException("Injected test audit failure");
            }).when(commentAudit).flush();
            response(500,"POST",c.path()+"/comments/"+id+"/hide",f.owner,Map.of("version",0,"reason","Must rollback"));
        }finally{reset(commentAudit);}
        assertTrue(afterAuditInsert.get(),"Actual audit row must exist in the source transaction before the injected failure");
        assertFalse(db().queryForObject("select resource_hidden from public.club_comments where id=?",Boolean.class,id));assertEquals(0,version(id));
        assertEquals(0,db().queryForObject("select count(*) from public.club_comment_moderation_actions where comment_id=?",Long.class,id));
    }
    @Test void realConstraintsRejectCrossSourceRootAndAmbiguousSourceWithoutChangingFixture()throws Exception {
        var f=fixture();var e=content(f,true,"UNIVERSITY_STUDENTS");var a=content(f,false,"UNIVERSITY_STUDENTS");long root=create(e,f.first),other=create(a,f.second);
        try(Connection connection=source.getConnection()){
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("update public.club_comments set root_id=? where id=?")){
                statement.setLong(1,root);statement.setLong(2,other);var error=assertThrows(SQLException.class,statement::executeUpdate);assertEquals("23503",error.getSQLState());
            }finally{connection.rollback();}
            try(var statement=connection.prepareStatement("update public.club_comments set event_id=? where id=?")){
                statement.setLong(1,e.id);statement.setLong(2,other);var error=assertThrows(SQLException.class,statement::executeUpdate);assertEquals("23514",error.getSQLState());
            }finally{connection.rollback();}
        }
        assertNull(db().queryForObject("select root_id from public.club_comments where id=?",Long.class,other));
    }
    @Test void moderationScopesAuditAndRuntimePrivilegesAreIndependent()throws Exception {
        var f=fixture();var c=content(f,true,"UNIVERSITY_STUDENTS");long id=create(c,f.first);
        response(200,"POST","/api/admin/club-comments/"+id+"/hide",f.admin,Map.of("version",0,"reason","Private platform reason"));
        response(200,"POST",c.path()+"/comments/"+id+"/hide",f.owner,Map.of("version",1,"reason","Resource reason"));
        response(200,"POST",c.path()+"/comments/"+id+"/restore",f.owner,Map.of("version",2,"reason","Restore local only"));
        var history=response(200,"GET",c.path()+"/comments/"+id+"/moderation-actions",f.owner,null);
        assertEquals(2,history.path("totalElements").asLong());assertFalse(history.toString().contains("Private platform reason"));
        response(404,"GET",c.path()+"/comments/"+id,f.first,null);
        assertFalse(db().queryForObject("select has_table_privilege(current_user,'public.club_comment_moderation_actions','UPDATE')",Boolean.class));
        assertFalse(db().queryForObject("select has_table_privilege(current_user,'public.club_comments','DELETE')",Boolean.class));
    }
    @Test void hundredCommentPageUsesBoundedScalarAndAggregateQueries()throws Exception {
        var f=fixture();for(boolean event:List.of(true,false)){
            var c=content(f,event,"UNIVERSITY_STUDENTS");
            db().update("insert into public.club_comments("+c.column()+",author_id,body,status,created_at,updated_at) select ?,?,'Fixture body','ACTIVE',now(),now() from generate_series(1,101)",c.id,f.first.id);
            var statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.clear();
            var page=response(200,"GET",c.path()+"/comments?size=100",f.second,null);
            assertEquals(100,page.path("items").size());assertTrue(page.path("hasNext").asBoolean());
            assertTrue(statistics.getPrepareStatementCount()<=20,"Bounded query count expected");
        }
    }
}
