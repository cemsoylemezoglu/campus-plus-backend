package com.campusplus.backend;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.CommentModerationRepository;
import com.campusplus.backend.service.notification.CommentNotificationWriter;
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

/** Prepared for separately approved V010 + limited test grants. No migration/reset/cleanup. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresCommentIT.DatabaseConfiguration.class)
class PostgresCommentIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean CommentModerationRepository commentAudit;
    @MockitoSpyBean CommentNotificationWriter socialWriter;
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
    long published(Actor actor)throws Exception{long id=student(actor);response(200,"POST","/api/me/posts/"+id+"/publish",actor,Map.of("version",0));return id;}
    String comments(long post){return "/api/posts/"+post+"/comments";}
    long comment(long post,Actor actor)throws Exception{return response(201,"POST",comments(post),actor,Map.of("body","Comment fixture")).path("id").asLong();}
    long reply(long post,long root,Actor actor)throws Exception{return response(201,"POST",comments(post)+"/"+root+"/replies",actor,Map.of("body","Reply fixture")).path("id").asLong();}
    JsonNode ownNotice(Actor actor,long comment)throws Exception{
        for(var n:response(200,"GET","/api/me/notifications?size=100",actor,null).path("content"))
            if(db().queryForObject("select comment_id from public.notifications where id=?",Long.class,n.path("id").asLong())!=null
                    &&db().queryForObject("select comment_id from public.notifications where id=?",Long.class,n.path("id").asLong())==comment)return n;
        throw new AssertionError("Expected own comment notification not found");
    }
    @Test void commentOwnershipEditRemovalAndReplyPlaceholderUseRealRows()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.second);reply(post,root,f.owner);
        response(404,"PATCH",comments(post)+"/"+root,f.first,Map.of("version",0,"body","Cannot rewrite"));
        response(200,"PATCH",comments(post)+"/"+root,f.second,Map.of("version",0,"body","Edited"));
        response(409,"PATCH",comments(post)+"/"+root,f.second,Map.of("version",0,"body","Stale"));
        response(200,"POST","/api/me/comments/"+root+"/remove",f.second,Map.of("version",1));
        var view=response(200,"GET",comments(post)+"/"+root,f.first,null);assertTrue(view.path("removed").asBoolean());assertTrue(view.path("body").isNull());assertTrue(view.path("author").isNull());assertEquals(1,view.path("replyCount").asLong());
        assertEquals(1,response(200,"GET",comments(post)+"/"+root+"/replies",f.first,null).path("items").size());
        response(404,"POST",comments(post)+"/"+root+"/replies",f.first,Map.of("body","Deleted root"));
        assertEquals(1,response(200,"GET","/api/posts/"+post+"/interactions",f.first,null).path("commentCount").asLong());
    }
    @Test void privateClubMembershipLossHidesEverythingButOwnWithdrawWorks()throws Exception{
        var f=fixture();var membership=join(f,f.first);long post=club(f,f.owner,"CLUB_MEMBERS");response(200,"POST",path(f,post)+"/publish",f.owner,Map.of("version",0));
        long root=comment(post,f.first);response(200,"PUT","/api/posts/"+post+"/like",f.first,null);
        response(404,"GET",comments(post),f.second,null);response(404,"PUT","/api/posts/"+post+"/like",f.second,null);
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",membership.path("version").asLong()));
        response(404,"GET",comments(post),f.first,null);response(404,"GET","/api/posts/"+post+"/interactions",f.first,null);
        assertEquals(204,request("DELETE","/api/posts/"+post+"/like",f.first,null).statusCode());
        response(200,"POST","/api/me/comments/"+root+"/remove",f.first,Map.of("version",0));
        assertFalse(db().queryForObject("select active from public.post_likes where post_id=? and user_id=?",Boolean.class,post,f.first.id));
    }
    @Test void sourceAndPlatformFlagsAreIndependentAndAuditRemainsScoped()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.second);
        response(200,"POST",comments(post)+"/"+root+"/hide",f.first,Map.of("version",0,"reason","Source reason"));
        response(200,"POST","/api/admin/comments/"+root+"/hide",f.admin,Map.of("version",1,"reason","Private platform reason"));
        response(200,"POST",comments(post)+"/"+root+"/restore",f.first,Map.of("version",2,"reason","Source restore"));
        response(404,"GET",comments(post)+"/"+root,f.second,null);
        var audit=response(200,"GET",comments(post)+"/"+root+"/moderation-actions",f.first,null);assertEquals(2,audit.path("totalElements").asLong());assertFalse(audit.toString().contains("Private platform reason"));
        response(200,"POST","/api/admin/comments/"+root+"/restore",f.admin,Map.of("version",3,"reason","Platform restore"));
        response(200,"GET",comments(post)+"/"+root,f.second,null);
        assertEquals(4,db().queryForObject("select count(*) from public.comment_moderation_actions where comment_id=?",Integer.class,root));
    }
    @Test void postAndCommentLikeRetriesAndRelikesDoNotDuplicateInbox()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.first);
        for(int i=0;i<2;i++){response(200,"PUT","/api/posts/"+post+"/like",f.second,null);response(200,"PUT",comments(post)+"/"+root+"/like",f.second,null);}
        assertEquals(1,db().queryForObject("select count(*) from public.post_likes where post_id=?",Integer.class,post));
        assertEquals(1,db().queryForObject("select count(*) from public.post_comment_likes where comment_id=?",Integer.class,root));
        for(String target:List.of("/api/posts/"+post+"/like",comments(post)+"/"+root+"/like")){
            for(int i=0;i<2;i++)assertEquals(204,request("DELETE",target,f.second,null).statusCode());response(200,"PUT",target,f.second,null);
        }
        assertEquals(2,db().queryForObject("select count(*) from public.notifications where post_id=?",Integer.class,post));
        assertEquals(1,db().queryForObject("select version from public.posts where id=?",Long.class,post));
    }
    @Test void replyToSourceOwnerProducesOneHigherPriorityNotification()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.first),child=reply(post,root,f.second);
        assertEquals(1,db().queryForObject("select count(*) from public.notifications where comment_id=?",Integer.class,child));
        assertEquals("COMMENT_REPLIED",db().queryForObject("select type from public.notifications where comment_id=?",String.class,child));
        assertEquals(f.first.id,db().queryForObject("select recipient_id from public.notifications where comment_id=?",Long.class,child));
        var notice=ownNotice(f.first,child);assertTrue(notice.path("available").asBoolean());assertTrue(notice.path("target").path("clubId").isNull());assertEquals(post,notice.path("target").path("postId").asLong());
        response(200,"POST",comments(post)+"/"+root+"/hide",f.first,Map.of("version",0,"reason","Hide thread"));
        assertFalse(ownNotice(f.first,child).path("available").asBoolean());assertTrue(ownNotice(f.first,child).path("target").isNull());
    }
    @Test void distinctRootAuthorAndPostAuthorEachReceiveOneEligibleNotice()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.owner),child=reply(post,root,f.second);
        assertEquals(Set.of(f.first.id,f.owner.id),new HashSet<>(db().queryForList("select recipient_id from public.notifications where comment_id=?",Long.class,child)));
        assertEquals(2,db().queryForObject("select count(*) from public.notifications where comment_id=?",Integer.class,child));
    }
    @Test void concurrentLikePutHasOneRowAndOneNotice()throws Exception{
        var f=fixture();long post=published(f.first);
        assertEquals(List.of(200,200),race(()->request("PUT","/api/posts/"+post+"/like",f.second,null).statusCode(),()->request("PUT","/api/posts/"+post+"/like",f.second,null).statusCode()));
        assertEquals(1,db().queryForObject("select count(*) from public.post_likes where post_id=?",Integer.class,post));
        assertEquals(1,db().queryForObject("select count(*) from public.notifications where post_id=?",Integer.class,post));
    }
    @Test void rootRemoveAndReplySerializeWithoutOrphanedContent()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.second);
        var results=race(()->request("POST",comments(post)+"/"+root+"/replies",f.owner,Map.of("body","Concurrent reply")).statusCode(),
            ()->request("POST","/api/me/comments/"+root+"/remove",f.second,Map.of("version",0)).statusCode());
        assertEquals(200,results.get(1));assertTrue(Set.of(201,404).contains(results.getFirst()));
        var rows=response(200,"GET",comments(post),f.first,null).path("items");
        if(results.getFirst()==201){assertEquals(1,rows.size());assertTrue(rows.get(0).path("removed").asBoolean());assertTrue(rows.get(0).path("body").isNull());}else assertEquals(0,rows.size());
    }
    @Test void commentNotificationFailureRollsBackCommentAndInboxAfterInsert()throws Exception{
        var f=fixture();long post=published(f.first);CommentNotificationWriter target=AopTestUtils.getUltimateTargetObject(socialWriter);
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("Controlled social rollback");}).when(target).created(any(),any());
        try{response(500,"POST",comments(post),f.second,Map.of("body","Rollback fixture"));}finally{reset(target);}
        assertEquals(0,db().queryForObject("select count(*) from public.post_comments where post_id=?",Integer.class,post));
        assertEquals(0,db().queryForObject("select count(*) from public.notifications where post_id=?",Integer.class,post));
    }
    @Test void auditFailureRollsBackCommentFlag()throws Exception{
        var f=fixture();long post=published(f.first),root=comment(post,f.second);
        doThrow(new IllegalStateException("Controlled audit rollback")).when(commentAudit).save(any());
        try{response(500,"POST","/api/admin/comments/"+root+"/hide",f.admin,Map.of("version",0,"reason","Rollback"));}finally{reset(commentAudit);}
        assertFalse(db().queryForObject("select platform_hidden from public.post_comments where id=?",Boolean.class,root));
        assertEquals(0,db().queryForObject("select version from public.post_comments where id=?",Long.class,root));
    }
    void rejected(String state,String sql,Object...args)throws Exception{
        try(var c=source.getConnection()){c.setAutoCommit(false);try(var stmt=c.prepareStatement(sql)){
            for(int i=0;i<args.length;i++)stmt.setObject(i+1,args[i]);assertEquals(state,assertThrows(SQLException.class,stmt::executeUpdate).getSQLState());
        }finally{c.rollback();}}
    }
    @Test void foreignKeysChecksUniqueAndAuditPrivilegesAreEnforcedByPostgres()throws Exception{
        var f=fixture();long post=published(f.first),other=published(f.second),root=comment(post,f.first),child=reply(post,root,f.second),foreign=comment(other,f.second);
        rejected("23503","update public.post_comments set root_id=? where id=?",foreign,child);
        rejected("23514","update public.post_comments set root_id=id where id=?",root);
        rejected("23514","update public.post_comments set version=-1 where id=?",root);
        rejected("23514","update public.post_comments set status='REMOVED' where id=?",root);
        rejected("42501","update public.comment_moderation_actions set reason='forbidden' where false");
        response(200,"PUT","/api/posts/"+post+"/like",f.second,null);
        rejected("23505","insert into public.post_likes(post_id,user_id,active,created_at,updated_at,version) values(?,?,true,now(),now(),0)",post,f.second.id);
        rejected("23514","update public.notifications set type='ANNOUNCEMENT_PUBLISHED' where post_id=?",post);
    }
    @Test void clubModerationDoesNotExtendToOtherManagersOrMarkerOnly()throws Exception{
        var f=fixture();long post=club(f,f.owner,"UNIVERSITY_STUDENTS");response(200,"POST",path(f,post)+"/publish",f.owner,Map.of("version",0));long root=comment(post,f.first);
        db().update("insert into public.user_roles(user_id,role) values(?,'CLUB_ADMIN')",f.second.id);
        response(403,"POST",comments(post)+"/"+root+"/hide",f.second,Map.of("version",0,"reason","Marker only"));
        response(200,"POST",comments(post)+"/"+root+"/hide",f.owner,Map.of("version",0,"reason","Local owner"));
        response(403,"POST","/api/admin/comments/"+root+"/restore",f.owner,Map.of("version",1,"reason","No platform access"));
    }
    @Test void privateClubNotificationsReachOnlyCurrentEligibleManagersAndReplyAuthor()throws Exception{
        var f=fixture();var member=join(f,f.first);join(f,f.second);
        var manager=response(200,"PATCH","/api/clubs/"+f.club+"/memberships/"+member.path("id").asLong()+"/role",f.owner,
            Map.of("version",member.path("version").asLong(),"role","MANAGER"));
        long post=club(f,f.owner,"CLUB_MEMBERS");response(200,"POST",path(f,post)+"/publish",f.owner,Map.of("version",0));long root=comment(post,f.second);
        assertEquals(Set.of(f.owner.id,f.first.id),new HashSet<>(db().queryForList("select recipient_id from public.notifications where comment_id=?",Long.class,root)));
        long child=reply(post,root,f.owner);
        assertEquals(Set.of(f.first.id,f.second.id),new HashSet<>(db().queryForList("select recipient_id from public.notifications where comment_id=?",Long.class,child)));
        assertTrue(ownNotice(f.first,child).path("available").asBoolean());
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",manager.path("version").asLong()));
        assertFalse(ownNotice(f.first,child).path("available").asBoolean());
    }
    @Test void cursorUsesRealSqlAndLaterHiddenRowsDoNotLeak()throws Exception{
        var f=fixture();long post=published(f.first),a=comment(post,f.second),b=comment(post,f.second),c=comment(post,f.second);
        var page=response(200,"GET",comments(post)+"?size=1",f.first,null);assertEquals(a,page.path("items").get(0).path("id").asLong());
        response(200,"POST","/api/admin/comments/"+b+"/hide",f.admin,Map.of("version",0,"reason","Skip hidden"));
        var next=response(200,"GET",comments(post)+"?size=1&cursor="+page.path("nextCursor").asText(),f.first,null);assertEquals(c,next.path("items").get(0).path("id").asLong());assertFalse(next.path("hasNext").asBoolean());
    }
    @Test void hundredCommentPageHasBoundedQueryCountAndSafeAuthorProjection()throws Exception{
        var f=fixture();long post=published(f.first);
        db().update("insert into public.post_comments(post_id,author_id,body,status,created_at,updated_at,version) select ?,?,'Page fixture '||s,'ACTIVE',now(),now(),0 from generate_series(1,100) s",post,f.second.id);
        var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
        var page=response(200,"GET",comments(post)+"?size=100",f.first,null);assertEquals(100,page.path("items").size());assertFalse(page.toString().contains("email"));
        assertTrue(stats.getPrepareStatementCount()<=18,"Comment query count must not grow per row");
    }
}
