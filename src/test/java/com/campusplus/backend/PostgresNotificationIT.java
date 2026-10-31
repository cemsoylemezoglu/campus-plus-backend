package com.campusplus.backend;

import com.campusplus.backend.entity.Club;
import com.campusplus.backend.security.JwtTestKeys;
import com.campusplus.backend.service.notification.NotificationWriter;
import com.campusplus.backend.support.PostgresTestDatabase;
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
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Requires explicitly approved V008 and test runner grants. Never skips missing schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresNotificationIT.DatabaseConfiguration.class)
class PostgresNotificationIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @MockitoSpyBean NotificationWriter writer;
    @Autowired com.campusplus.backend.repository.AnnouncementRepository announcements;
    @Autowired com.campusplus.backend.repository.UserRepository users;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.from(System::getenv).registerProperties(registry);
        String secret=JwtTestKeys.newSecret();registry.add("JWT_SECRET",() -> secret);
        registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto",() -> "validate");
        registry.add("spring.jpa.show-sql",() -> "false");
        registry.add("spring.jpa.properties.hibernate.generate_statistics",() -> "true");registry.add("server.address",() -> "127.0.0.1");
    }
    @TestConfiguration(proxyBeanMethods=false) static class DatabaseConfiguration {
        @Bean DataSource dataSource(){return PostgresTestDatabase.from(System::getenv).writableDataSource();}
    }
    private static final class Actor { final long id;final String token;Actor(long id,String token){this.id=id;this.token=token;} }
    private record Fixture(Actor admin,Actor owner,Actor first,Actor second,long university,long club) {}
    JdbcTemplate db(){return new JdbcTemplate(source);}
    String unique(){return UUID.randomUUID().toString().replace("-","");}
    HttpResponse<String> request(String method,String path,Actor actor,Object body)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(40)).header("Content-Type","application/json");
        if(actor!=null)builder.header("Authorization","Bearer "+actor.token);
        return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode response(int status,String method,String path,Actor actor,Object body)throws Exception {
        var response=request(method,path,actor,body);assertEquals(status,response.statusCode(),method+" "+path+" status");
        assertFalse(response.body().contains("passwordHash"));assertFalse(response.body().contains("password_hash"));
        return json.readTree(response.body());
    }
    Actor actor(boolean admin)throws Exception {
        String email="notification-it-"+unique()+"@example.test",password=UUID.randomUUID()+"!a9";
        long id=response(201,"POST","/api/auth/register",null,Map.of("name","Notification Student","email",email,"password",password)).path("id").asLong();
        if(admin)db().update("insert into public.user_roles(user_id,role) values(?, 'ADMIN')",id);
        String token=response(200,"POST","/api/auth/login",null,Map.of("email",email,"password",password)).path("accessToken").asText();
        assertFalse(token.isBlank());return new Actor(id,token);
    }
    long university(Actor admin)throws Exception{return response(201,"POST","/api/universities",admin,
        Map.of("code","EI_"+unique().substring(0,20),"name","Notification University","city","Ankara","countryCode","TR")).path("id").asLong();}
    void profile(Actor actor,long university)throws Exception{response(201,"POST","/api/me/student-profile",actor,Map.of("universityId",university,"department","Engineering"));}
    Fixture fixture()throws Exception {
        Actor admin=actor(true),owner=actor(false),first=actor(false),second=actor(false);long university=university(admin);
        for(var a:List.of(owner,first,second))profile(a,university);
        var club=response(201,"POST","/api/clubs",owner,Map.of("name","Notification Club","slug","ei-"+unique(),"description","Notification test fixture"));
        long id=club.path("club").path("id").asLong();response(200,"POST","/api/admin/clubs/"+id+"/approve",admin,Map.of("version",club.path("version").asLong()));
        return new Fixture(admin,owner,first,second,university,id);
    }
    private <T> T transaction(java.util.function.Supplier<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> work.get());
    }
    JsonNode join(Fixture f,Actor actor)throws Exception {
        var applied=response(201,"POST","/api/clubs/"+f.club+"/membership-requests",actor,Map.of());
        return response(200,"POST","/api/clubs/"+f.club+"/memberships/"+applied.path("id").asLong()+"/approve",f.owner,Map.of("version",applied.path("version").asLong()));
    }
    long draft(Fixture f,Actor manager)throws Exception {
        return response(201,"POST","/api/clubs/"+f.club+"/announcements",manager,Map.of("title","Notification source","body","Members only","audience","CLUB_MEMBERS")).path("announcement").path("id").asLong();
    }
    void publish(Fixture f,Actor manager,long id)throws Exception {
        response(200,"POST","/api/clubs/"+f.club+"/announcements/"+id+"/publish",manager,Map.of("version",0));
    }
    long notice(long announcement,long recipient){return db().queryForObject("select id from public.notifications where announcement_id=? and recipient_id=?",Long.class,announcement,recipient);}
    JsonNode notification(Actor actor,long id)throws Exception {
        var page=response(200,"GET","/api/me/notifications?size=100",actor,null);
        for(var n:page.path("content"))if(n.path("id").asLong()==id)return n;
        throw new AssertionError("Expected own notification missing");
    }
    List<Integer> race(Callable<Integer> first,Callable<Integer> second)throws Exception {
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> {start.await();return first.call();});var b=executor.submit(() -> {start.await();return second.call();});
            start.countDown();return List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));
        }
    }
    long event(Fixture f,String audience)throws Exception {
        Instant now=Instant.now();var created=response(201,"POST","/api/clubs/"+f.club+"/events",f.owner,Map.of("title","Notification event","description","Description","venue","Hall","audience",audience,
            "registrationOpensAt",now.minusSeconds(60).toString(),"registrationClosesAt",now.plusSeconds(600).toString(),"startsAt",now.plusSeconds(1200).toString(),"endsAt",now.plusSeconds(3600).toString(),"timeZone","Europe/Istanbul"));
        long id=created.path("event").path("id").asLong();response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/publish",f.owner,Map.of("version",0));return id;
    }
    long register(long event,Actor actor)throws Exception {return response(201,"POST","/api/events/"+event+"/participations",actor,Map.of("eventVersion",1)).path("id").asLong();}

    @Test void publicationFreezesEligibleRecipientsAndReplayDoesNotDuplicate()throws Exception {
        var f=fixture();join(f,f.first);long id=draft(f,f.owner);publish(f,f.owner,id);
        assertEquals(List.of(f.first.id),db().queryForList("select recipient_id from public.notifications where announcement_id=?",Long.class,id));
        transaction(() -> {writer.announcementPublished(announcements.findById(id).orElseThrow(),users.findById(f.owner.id).orElseThrow());return null;});
        assertEquals(1,db().queryForObject("select count(*) from public.notifications where announcement_id=?",Integer.class,id));
        join(f,f.second);assertEquals(0,db().queryForObject("select count(*) from public.notifications where announcement_id=? and recipient_id=?",Integer.class,id,f.second.id));
    }
    @Test void readUnreadReadAllAndAdminIsolationUseRealRows()throws Exception {
        var f=fixture();join(f,f.first);long first=draft(f,f.owner);publish(f,f.owner,first);long id=notice(first,f.first.id);
        response(404,"POST","/api/me/notifications/"+id+"/read",f.admin,Map.of("version",0));
        response(404,"POST","/api/me/notifications/"+id+"/unread",f.second,Map.of("version",0));
        response(200,"POST","/api/me/notifications/"+id+"/read",f.first,Map.of("version",0));
        assertEquals(1,response(200,"POST","/api/me/notifications/"+id+"/read",f.first,Map.of("version",0)).path("version").asLong());
        response(409,"POST","/api/me/notifications/"+id+"/unread",f.first,Map.of("version",0));
        response(200,"POST","/api/me/notifications/"+id+"/unread",f.first,Map.of("version",1));
        long newer=draft(f,f.owner);publish(f,f.owner,newer);
        response(200,"POST","/api/me/notifications/read-all",f.first,Map.of("upToId",id));
        assertEquals(1,response(200,"GET","/api/me/notifications/unread-count",f.first,null).path("count").asLong());
        assertNull(db().queryForObject("select read_at from public.notifications where id=?",java.sql.Timestamp.class,notice(newer,f.first.id)));
        assertTrue(db().queryForObject("select count(*) from public.notifications where recipient_id=? and read_at is null",Long.class,f.owner.id)>0);
    }
    @Test void lostMembershipRedactsSourceWithoutLosingPersonalInbox()throws Exception {
        var f=fixture();var membership=join(f,f.first);long a=draft(f,f.owner);publish(f,f.owner,a);long id=notice(a,f.first.id);
        assertTrue(notification(f.first,id).path("available").asBoolean());
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",membership.path("version").asLong()));
        var n=notification(f.first,id);assertFalse(n.path("available").asBoolean());assertTrue(n.path("target").isNull());
        response(200,"POST","/api/me/notifications/"+id+"/read",f.first,Map.of("version",0));
    }
    @Test void crossManagerPublicationDoesNotDeadlockOnRecipientForeignKeys()throws Exception {
        var f=fixture();var first=join(f,f.first);join(f,f.second);
        response(200,"PATCH","/api/clubs/"+f.club+"/memberships/"+first.path("id").asLong()+"/role",f.owner,Map.of("version",first.path("version").asLong(),"role","MANAGER"));
        long a=draft(f,f.owner),b=draft(f,f.first);
        var results=race(() -> request("POST","/api/clubs/"+f.club+"/announcements/"+a+"/publish",f.owner,Map.of("version",0)).statusCode(),
            () -> request("POST","/api/clubs/"+f.club+"/announcements/"+b+"/publish",f.first,Map.of("version",0)).statusCode());
        assertEquals(List.of(200,200),results);
        assertEquals(4,db().queryForObject("select count(*) from public.notifications where announcement_id in (?,?)",Integer.class,a,b));
    }
    @Test void staleUnreadCannotUndoConcurrentReadAll()throws Exception {
        var f=fixture();join(f,f.first);long a=draft(f,f.owner);publish(f,f.owner,a);long id=notice(a,f.first.id);
        var statuses=race(() -> request("POST","/api/me/notifications/read-all",f.first,Map.of("upToId",id)).statusCode(),
            () -> request("POST","/api/me/notifications/"+id+"/unread",f.first,Map.of("version",0)).statusCode());
        assertEquals(200,statuses.get(0));assertTrue(Set.of(200,409).contains(statuses.get(1)));
        assertNotNull(db().queryForObject("select read_at from public.notifications where id=?",java.sql.Timestamp.class,id));
    }
    @Test void failureAfterNotificationInsertRollsBackPublicationAndInbox()throws Exception {
        var f=fixture();join(f,f.first);long a=draft(f,f.owner);
        NotificationWriter target=AopTestUtils.getUltimateTargetObject(writer);clearInvocations(target);
        doAnswer(i -> {i.callRealMethod();throw new DataIntegrityViolationException("Controlled notification rollback fixture");}).when(target).announcementPublished(any(),any());
        try {
            response(409,"POST","/api/clubs/"+f.club+"/announcements/"+a+"/publish",f.owner,Map.of("version",0));
            assertEquals("DRAFT",db().queryForObject("select status from public.club_announcements where id=?",String.class,a));
            assertEquals(0,db().queryForObject("select count(*) from public.notifications where announcement_id=?",Integer.class,a));
        } finally {doCallRealMethod().when(target).announcementPublished(any(),any());}
    }
    @Test void manualEventCancellationNotifiesRegisteredAndAttendedExactlyOnce()throws Exception {
        var f=fixture();long e=event(f,"UNIVERSITY_STUDENTS"),p1=register(e,f.first),p2=register(e,f.second);
        response(200,"POST","/api/clubs/"+f.club+"/events/"+e+"/participations/"+p1+"/check-in",f.owner,Map.of("version",0));
        response(200,"POST","/api/clubs/"+f.club+"/events/"+e+"/cancel",f.owner,Map.of("version",1,"reason","Cancelled"));
        assertEquals(2,db().queryForObject("select count(*) from public.notifications where event_id=? and type='EVENT_CANCELLED'",Integer.class,e));
        assertEquals(0,db().queryForObject("select count(*) from public.notifications where event_id=? and type='PARTICIPATION_CANCELLED'",Integer.class,e));
        assertEquals("ATTENDED",db().queryForObject("select status from public.event_participations where id=?",String.class,p1));
        assertEquals("CANCELLED",db().queryForObject("select status from public.event_participations where id=?",String.class,p2));
    }
    @Test void membershipEndAndArchiveOnlyNotifyActuallyEndedTickets()throws Exception {
        var f=fixture();var m=join(f,f.first);long privateEvent=event(f,"CLUB_MEMBERS"),publicEvent=event(f,"UNIVERSITY_STUDENTS");
        long privateTicket=register(privateEvent,f.first),publicTicket=register(publicEvent,f.first);
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",m.path("version").asLong()));
        long version=db().queryForObject("select version from public.clubs where id=?",Long.class,f.club);
        response(200,"POST","/api/clubs/"+f.club+"/archive",f.admin,Map.of("version",version));
        for(long p:new long[]{privateTicket,publicTicket})assertEquals(1,db().queryForObject("select count(*) from public.notifications where participation_id=? and type='PARTICIPATION_CANCELLED'",Integer.class,p));
        assertEquals(0,db().queryForObject("select count(*) from public.notifications where event_id in (?,?) and type='EVENT_CANCELLED'",Integer.class,privateEvent,publicEvent));
    }
    @Test void databaseConstraintsAndAccountDeleteActionsRollbackCleanly()throws Exception {
        var f=fixture();join(f,f.first);long a=draft(f,f.owner);publish(f,f.owner,a);long id=notice(a,f.first.id);
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            for(String sql:List.of("update public.notifications set read_at=created_at-interval '1 second' where id=?",
                    "update public.notifications set announcement_id=null where id=?")) {
                try(var stmt=c.prepareStatement(sql)){stmt.setLong(1,id);assertEquals("23514",assertThrows(SQLException.class,stmt::executeUpdate).getSQLState());}finally{c.rollback();}
            }
        }
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try(var stmt=c.prepareStatement("insert into public.notifications(recipient_id,actor_id,type,source_key,club_id,announcement_id,event_id,participation_id,membership_id,created_at,version) select recipient_id,actor_id,type,source_key,club_id,announcement_id,event_id,participation_id,membership_id,created_at,version from public.notifications where id=?")) {
                stmt.setLong(1,id);assertEquals("23505",assertThrows(SQLException.class,stmt::executeUpdate).getSQLState());
            } finally {c.rollback();}
            try(var stmt=c.prepareStatement("update public.notifications set announcement_id=9223372036854775807 where id=?")) {
                stmt.setLong(1,id);assertEquals("23503",assertThrows(SQLException.class,stmt::executeUpdate).getSQLState());
            } finally {c.rollback();}
        }
        Actor disposable=actor(false);
        String key=unique();db().update("insert into public.notifications(recipient_id,actor_id,type,source_key,club_id,created_at) values(?,?,'CLUB_APPROVED',?,?,now()),(?,?,'CLUB_APPROVED',?,?,now())",
            disposable.id,f.owner.id,key,f.club,f.first.id,disposable.id,key,f.club);
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            // Match JPA account deletion: the existing user_roles FK is NO ACTION, not cascade.
            // Only this newly created disposable fixture is touched; rollback restores its role too.
            try(var role=c.prepareStatement("delete from public.user_roles where user_id=?")) {
                role.setLong(1,disposable.id);assertEquals(1,role.executeUpdate());
            }
            try(var delete=c.prepareStatement("delete from public.users where id=?")) {
                delete.setLong(1,disposable.id);assertEquals(1,delete.executeUpdate());
                try(var check=c.prepareStatement("select count(*),count(actor_id) from public.notifications where source_key=?")) {
                    check.setString(1,key);try(var rows=check.executeQuery()){assertTrue(rows.next());assertEquals(1,rows.getLong(1));assertEquals(0,rows.getLong(2));}
                }
            } finally {c.rollback();}
        }
        assertEquals(1,db().queryForObject("select count(*) from public.users where id=?",Integer.class,disposable.id));
    }
    @Test void hundredDistinctSourcesUseBoundedSqlInsteadOfNPlusOne()throws Exception {
        var f=fixture();join(f,f.first);String prefix="bulk-"+unique()+":";
        db().update("with created as (insert into public.club_announcements(club_id,created_by,title,body,audience,status,published_at,created_at,updated_at) "
            +"select ?,?,'Batch source','Body','CLUB_MEMBERS','PUBLISHED',now(),now(),now() from generate_series(1,120) returning id) "
            +"insert into public.notifications(recipient_id,actor_id,type,source_key,club_id,announcement_id,created_at) select ?,?,'ANNOUNCEMENT_PUBLISHED',? || id,?,id,now() from created",
            f.club,f.owner.id,f.first.id,f.owner.id,prefix,f.club);
        var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
        var page=response(200,"GET","/api/me/notifications?size=100",f.first,null);
        assertEquals(100,page.path("content").size());assertTrue(stats.getPrepareStatementCount()>=3);
        assertTrue(stats.getPrepareStatementCount()<=14,"Inbox exceeded bounded SQL budget");
    }
    @Test void writerRequiresExistingSourceTransaction()throws Exception {
        var f=fixture();long a=draft(f,f.owner);
        var announcement=announcements.findById(a).orElseThrow();var actor=users.findById(f.owner.id).orElseThrow();
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,() -> writer.announcementPublished(announcement,actor));
    }
}
