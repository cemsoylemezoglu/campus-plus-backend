package com.campusplus.backend;

import com.campusplus.backend.entity.Club;
import com.campusplus.backend.security.JwtTestKeys;
import com.campusplus.backend.service.announcement.AnnouncementLifecycleService;
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

/** Requires explicitly approved V007 and test runner grants. Never skips missing schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresAnnouncementIT.DatabaseConfiguration.class)
class PostgresAnnouncementIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @MockitoSpyBean AnnouncementLifecycleService lifecycle;
    @Autowired com.campusplus.backend.service.club.ClubWorkspace workspace;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.from(System::getenv).registerProperties(registry);
        String secret=JwtTestKeys.newSecret();registry.add("JWT_SECRET",() -> secret);
        registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto",() -> "validate");
        registry.add("spring.jpa.show-sql",() -> "false");registry.add("server.address",() -> "127.0.0.1");
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
        String email="announcement-it-"+unique()+"@example.test",password=UUID.randomUUID()+"!a9";
        long id=response(201,"POST","/api/auth/register",null,Map.of("name","Announcement Student","email",email,"password",password)).path("id").asLong();
        if(admin)db().update("insert into public.user_roles(user_id,role) values(?, 'ADMIN')",id);
        String token=response(200,"POST","/api/auth/login",null,Map.of("email",email,"password",password)).path("accessToken").asText();
        assertFalse(token.isBlank());return new Actor(id,token);
    }
    long university(Actor admin)throws Exception{return response(201,"POST","/api/universities",admin,
        Map.of("code","EI_"+unique().substring(0,20),"name","Announcement University","city","Ankara","countryCode","TR")).path("id").asLong();}
    void profile(Actor actor,long university)throws Exception{response(201,"POST","/api/me/student-profile",actor,Map.of("universityId",university,"department","Engineering"));}
    Fixture fixture()throws Exception {
        Actor admin=actor(true),owner=actor(false),first=actor(false),second=actor(false);long university=university(admin);
        for(var a:List.of(owner,first,second))profile(a,university);
        var club=response(201,"POST","/api/clubs",owner,Map.of("name","Announcement Club","slug","ei-"+unique(),"description","Announcement test fixture"));
        long id=club.path("club").path("id").asLong();response(200,"POST","/api/admin/clubs/"+id+"/approve",admin,Map.of("version",club.path("version").asLong()));
        return new Fixture(admin,owner,first,second,university,id);
    }
    long create(Fixture f,String audience)throws Exception {
        return response(201,"POST","/api/clubs/"+f.club+"/announcements",f.owner,
            Map.of("title","Announcement fixture","body","Plain text","audience",audience)).path("announcement").path("id").asLong();
    }
    String action(Fixture f,long id,String action){return "/api/clubs/"+f.club+"/announcements/"+id+"/"+action;}
    void publish(Fixture f,long id)throws Exception {response(200,"POST",action(f,id,"publish"),f.owner,Map.of("version",0));}
    JsonNode join(Fixture f,Actor student)throws Exception {
        var request=response(201,"POST","/api/clubs/"+f.club+"/membership-requests",student,Map.of());
        return response(200,"POST","/api/clubs/"+f.club+"/memberships/"+request.path("id").asLong()+"/approve",f.owner,Map.of("version",request.path("version").asLong()));
    }
    List<Integer> race(Callable<Integer> first,Callable<Integer> second)throws Exception {
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> {start.await();return first.call();});
            var b=executor.submit(() -> {start.await();return second.call();});
            start.countDown();return List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));
        }
    }
    @Test void lifecycleUsesRealVersionAndKeepsWithdrawalPrivate()throws Exception {
        var f=fixture();long id=create(f,"UNIVERSITY_STUDENTS");
        response(404,"GET","/api/announcements/"+id,f.first,null);publish(f,id);
        response(200,"GET","/api/announcements/"+id,f.first,null);
        var edited=response(200,"PATCH","/api/clubs/"+f.club+"/announcements/"+id,f.owner,Map.of("version",1,"body","Corrected body"));
        assertEquals(2,edited.path("announcement").path("version").asLong());
        response(200,"POST",action(f,id,"withdraw"),f.owner,Map.of("version",2,"reason","Correction required"));
        response(404,"GET","/api/announcements/"+id,f.first,null);
        var privateView=response(200,"GET","/api/clubs/"+f.club+"/announcements/"+id+"/manage",f.owner,null);
        assertEquals("Correction required",privateView.path("withdrawalReason").asText());
        var generalView=response(200,"GET","/api/announcements/"+id,f.owner,null);assertFalse(generalView.has("withdrawalReason"));
        response(200,"POST",action(f,id,"archive"),f.owner,Map.of("version",3));
        assertEquals("WITHDRAWN",db().queryForObject("select status from public.club_announcements where id=?",String.class,id));
        assertNotNull(db().queryForObject("select archived_at from public.club_announcements where id=?",java.sql.Timestamp.class,id));
    }
    @Test void discoveryFiltersBeforePaginationAndReflectsMembershipLoss()throws Exception {
        var f=fixture();long publicId=create(f,"UNIVERSITY_STUDENTS"),privateId=create(f,"CLUB_MEMBERS");publish(f,publicId);publish(f,privateId);
        var page=response(200,"GET","/api/announcements?clubId="+f.club+"&size=1",f.first,null);
        assertEquals(1,page.path("totalElements").asLong());assertEquals(publicId,page.path("content").get(0).path("id").asLong());
        response(404,"GET","/api/announcements/"+privateId,f.first,null);
        var membership=join(f,f.first);
        assertEquals(2,response(200,"GET","/api/announcements?clubId="+f.club+"&size=1",f.first,null).path("totalElements").asLong());
        response(200,"GET","/api/announcements/"+privateId,f.first,null);
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",membership.path("version").asLong()));
        response(404,"GET","/api/announcements/"+privateId,f.first,null);
        assertEquals(1,response(200,"GET","/api/announcements?clubId="+f.club,f.first,null).path("totalElements").asLong());
    }
    @Test void foreignUniversityAndMarkerOnlyCannotReadOrManage()throws Exception {
        var f=fixture();long id=create(f,"UNIVERSITY_STUDENTS");publish(f,id);
        long elsewhere=university(f.admin);
        response(200,"PUT","/api/me/student-profile",f.second,Map.of("version",0,"universityId",elsewhere,"department","Engineering"));
        response(404,"GET","/api/announcements/"+id,f.second,null);
        assertEquals(0,response(200,"GET","/api/announcements?clubId="+f.club,f.second,null).path("totalElements").asLong());
        db().update("insert into public.user_roles(user_id,role) values(?,'CLUB_ADMIN')",f.first.id);
        response(403,"POST",action(f,id,"withdraw"),f.first,Map.of("version",1,"reason","Cannot moderate"));
        response(403,"GET","/api/clubs/"+f.club+"/announcements/manage",f.first,null);
    }
    @Test void simultaneousPublicationHasOneWinner()throws Exception {
        var f=fixture();long id=create(f,"UNIVERSITY_STUDENTS");
        var statuses=race(() -> request("POST",action(f,id,"publish"),f.owner,Map.of("version",0)).statusCode(),
            () -> request("POST",action(f,id,"publish"),f.admin,Map.of("version",0)).statusCode());
        assertEquals(List.of(200,409),statuses.stream().sorted().toList());
        assertEquals(1,db().queryForObject("select version from public.club_announcements where id=?",Long.class,id));
    }
    @Test void publicationAndClubArchiveCannotLeaveLiveAnnouncement()throws Exception {
        var f=fixture();long id=create(f,"UNIVERSITY_STUDENTS");
        long clubVersion=db().queryForObject("select version from public.clubs where id=?",Long.class,f.club);
        var statuses=race(() -> request("POST",action(f,id,"publish"),f.owner,Map.of("version",0)).statusCode(),
            () -> request("POST","/api/clubs/"+f.club+"/archive",f.admin,Map.of("version",clubVersion)).statusCode());
        assertEquals(200,statuses.get(1));assertTrue(Set.of(200,403,409).contains(statuses.get(0)));
        assertEquals(0,db().queryForObject("select count(*) from public.club_announcements where club_id=? and (archived_at is null or status='PUBLISHED')",Integer.class,f.club));
        response(404,"GET","/api/announcements/"+id,f.first,null);
    }
    @Test void eventLinksRequirePublishedSameClubAndCompatibleAudience()throws Exception {
        var f=fixture();Instant now=Instant.now();
        var event=response(201,"POST","/api/clubs/"+f.club+"/events",f.owner,Map.of("title","Private event","description","Description","venue","Hall",
            "audience","CLUB_MEMBERS","registrationOpensAt",now.minusSeconds(30).toString(),"registrationClosesAt",now.plusSeconds(1800).toString(),
            "startsAt",now.plusSeconds(3600).toString(),"endsAt",now.plusSeconds(7200).toString(),"timeZone","Europe/Istanbul"));
        long eventId=event.path("event").path("id").asLong();
        response(409,"POST","/api/clubs/"+f.club+"/announcements",f.owner,Map.of("title","Invalid link","body","Body","eventId",eventId));
        var draft=response(201,"POST","/api/clubs/"+f.club+"/announcements",f.owner,Map.of("title","Valid link","body","Body","audience","CLUB_MEMBERS","eventId",eventId));
        long id=draft.path("announcement").path("id").asLong();
        response(409,"POST",action(f,id,"publish"),f.owner,Map.of("version",0));
        response(200,"POST","/api/clubs/"+f.club+"/events/"+eventId+"/publish",f.owner,Map.of("version",0));publish(f,id);
        response(404,"GET","/api/announcements/"+id,f.first,null);
        join(f,f.first);
        assertEquals(eventId,response(200,"GET","/api/announcements/"+id,f.first,null).path("event").path("id").asLong());
    }
    @Test void failureAfterAnnouncementArchiveRollsBackWholeClubOperation()throws Exception {
        var f=fixture();long id=create(f,"UNIVERSITY_STUDENTS");publish(f,id);
        AnnouncementLifecycleService target=AopTestUtils.getUltimateTargetObject(lifecycle);
        clearInvocations(target);
        doAnswer(i -> {i.callRealMethod();throw new DataIntegrityViolationException("Controlled rollback fixture");})
            .when(target).clubArchived(any(Club.class),any(),any());
        try {
            long clubVersion=db().queryForObject("select version from public.clubs where id=?",Long.class,f.club);
            response(409,"POST","/api/clubs/"+f.club+"/archive",f.admin,Map.of("version",clubVersion));
            verify(target).clubArchived(argThat(c -> c.getId()==f.club),any(),any());
            assertEquals("ACTIVE",db().queryForObject("select status from public.clubs where id=?",String.class,f.club));
            assertEquals("PUBLISHED",db().queryForObject("select status from public.club_announcements where id=?",String.class,id));
            assertNull(db().queryForObject("select archived_at from public.club_announcements where id=?",java.sql.Timestamp.class,id));
            assertEquals(1,db().queryForObject("select count(*) from public.user_roles where user_id=? and role='CLUB_ADMIN'",Integer.class,f.owner.id));
        } finally {doCallRealMethod().when(target).clubArchived(any(Club.class),any(),any());}
    }
    @Test void databaseChecksAndForeignKeysRollbackInvalidWrites()throws Exception {
        var f=fixture();long id=create(f,"UNIVERSITY_STUDENTS");
        try(var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("update public.club_announcements set status='WITHDRAWN' where id=?")) {
                statement.setLong(1,id);assertEquals("23514",assertThrows(SQLException.class,statement::executeUpdate).getSQLState());
            } finally {connection.rollback();}
            try(var statement=connection.prepareStatement("update public.club_announcements set event_id=? where id=?")) {
                statement.setLong(1,Long.MAX_VALUE);statement.setLong(2,id);assertEquals("23503",assertThrows(SQLException.class,statement::executeUpdate).getSQLState());
            } finally {connection.rollback();}
        }
        assertEquals("DRAFT",db().queryForObject("select status from public.club_announcements where id=?",String.class,id));
    }
    private <T> T transaction(java.util.function.Supplier<T> work) {
        var template=new org.springframework.transaction.support.TransactionTemplate(transactions);
        template.setTimeout(15);return template.execute(status -> work.get());
    }
    @Test void crossRecipientKeyShareRemainsCompatibleWithBusinessUserLocks()throws Exception {
        var f=fixture();var barrier=new CyclicBarrier(2);
        Callable<Integer> first=() -> transaction(() -> {
            db().execute("set local lock_timeout='3s'");workspace.lockUser(f.owner.id);
            try {barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
            return db().queryForObject("select id from public.users where id=? for key share",Long.class,f.admin.id)>0?1:0;
        });
        Callable<Integer> second=() -> transaction(() -> {
            db().execute("set local lock_timeout='3s'");workspace.lockUser(f.admin.id);
            try {barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
            return db().queryForObject("select id from public.users where id=? for key share",Long.class,f.owner.id)>0?1:0;
        });
        assertEquals(List.of(1,1),race(first,second));
    }
    @Test void businessUserLockStillSerializesCompetingWriters()throws Exception {
        var f=fixture();var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var holder=executor.submit(() -> transaction(() -> {
                workspace.lockUser(f.owner.id);held.countDown();
                try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Lock fixture timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return true;
            }));
            try {
                assertTrue(held.await(10,TimeUnit.SECONDS));
                var failure=assertThrows(org.springframework.dao.DataAccessException.class,() -> transaction(() -> {
                    db().execute("set local lock_timeout='300ms'");return workspace.lockUser(f.owner.id);
                }));
                Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
                assertTrue(cause instanceof SQLException);assertEquals("55P03",((SQLException)cause).getSQLState());
            } finally {release.countDown();}
            assertTrue(holder.get(15,TimeUnit.SECONDS));
        }
    }

}
