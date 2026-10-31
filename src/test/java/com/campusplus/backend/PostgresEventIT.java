package com.campusplus.backend;

import com.campusplus.backend.entity.Club;
import com.campusplus.backend.security.JwtTestKeys;
import com.campusplus.backend.service.event.EventLifecycleService;
import com.campusplus.backend.support.PostgresTestDatabase;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import com.campusplus.backend.config.JwtConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Requires explicitly approved V006 and test runner grants. Never skips missing schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresEventIT.DatabaseConfiguration.class)
class PostgresEventIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired AdvancingClock clock;
    @Autowired JwtEncoder encoder;
    @MockitoSpyBean EventLifecycleService lifecycle;
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
        @Bean @Primary AdvancingClock eventClock(){return new AdvancingClock();}
    }
    static class AdvancingClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(Instant.now());
        public Instant instant(){return value.get();}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
    }
    @BeforeEach void resetClock(){clock.value.set(Instant.now());}
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
        String email="event-it-"+unique()+"@example.test",password=UUID.randomUUID()+"!a9";
        long id=response(201,"POST","/api/auth/register",null,Map.of("name","Event Student","email",email,"password",password)).path("id").asLong();
        if(admin)db().update("insert into public.user_roles(user_id,role) values(?, 'ADMIN')",id);
        String token=response(200,"POST","/api/auth/login",null,Map.of("email",email,"password",password)).path("accessToken").asText();
        assertFalse(token.isBlank());return new Actor(id,token);
    }
    long university(Actor admin)throws Exception{return response(201,"POST","/api/universities",admin,
        Map.of("code","EI_"+unique().substring(0,20),"name","Event University","city","Ankara","countryCode","TR")).path("id").asLong();}
    void profile(Actor actor,long university)throws Exception{response(201,"POST","/api/me/student-profile",actor,Map.of("universityId",university,"department","Engineering"));}
    Fixture fixture()throws Exception {
        Actor admin=actor(true),owner=actor(false),first=actor(false),second=actor(false);long university=university(admin);
        for(var a:List.of(owner,first,second))profile(a,university);
        var club=response(201,"POST","/api/clubs",owner,Map.of("name","Event Club","slug","ei-"+unique(),"description","Event test fixture"));
        long id=club.path("club").path("id").asLong();response(200,"POST","/api/admin/clubs/"+id+"/approve",admin,Map.of("version",club.path("version").asLong()));
        return new Fixture(admin,owner,first,second,university,id);
    }
    long event(Fixture f,String audience,int capacity)throws Exception {
        Instant now=clock.instant();var created=response(201,"POST","/api/clubs/"+f.club+"/events",f.owner,
            Map.of("title","Event fixture","description","Description","venue","Hall","audience",audience,"capacity",capacity,
                "registrationOpensAt",now.minusSeconds(60).toString(),"registrationClosesAt",now.plusSeconds(1800).toString(),
                "startsAt",now.plusSeconds(3600).toString(),"endsAt",now.plusSeconds(7200).toString(),"timeZone","Europe/Istanbul"));
        long id=created.path("event").path("id").asLong();
        response(404,"GET","/api/events/"+id,f.first,null);
        response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/publish",f.owner,Map.of("version",created.path("event").path("version").asLong()));
        return id;
    }
    long version(long id){return db().queryForObject("select version from public.events where id=?",Long.class,id);}
    long occupied(long id){return db().queryForObject("select count(*) from public.event_participations where event_id=? and status in ('REGISTERED','ATTENDED')",Long.class,id);}
    Actor renew(Actor actor){
        Instant now=clock.instant();var claims=JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
            .subject(Long.toString(actor.id)).issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        return new Actor(actor.id,encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue());
    }
    JsonNode register(long id,Actor actor)throws Exception{return response(201,"POST","/api/events/"+id+"/participations",actor,Map.of("eventVersion",version(id)));}
    List<Integer> parallel(Callable<Integer> first,Callable<Integer> second)throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var tasks=new ArrayList<Future<Integer>>();
            for(var action:List.of(first,second))tasks.add(executor.submit(() -> {ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return action.call();}));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            return List.of(tasks.get(0).get(50,TimeUnit.SECONDS),tasks.get(1).get(50,TimeUnit.SECONDS));
        }
    }
    @Test void registrationCancellationAndReregistrationUseOneVersionedRow()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",2);var p=register(id,f.first);
        response(409,"POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version(id)));
        var cancelled=response(200,"POST","/api/events/"+id+"/participation/cancel",f.first,Map.of("version",p.path("version").asLong()));
        var again=response(200,"POST","/api/events/"+id+"/participations",f.first,
            Map.of("eventVersion",version(id),"participationVersion",cancelled.path("version").asLong()));
        assertEquals(p.path("id").asLong(),again.path("id").asLong());assertEquals(1,occupied(id));
        response(403,"GET","/api/clubs/"+f.club+"/events/"+id+"/participations",f.first,null);
    }
    @Test void lastSeatHasExactlyOneWinnerAcrossIndependentHttpTransactions()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",1),version=version(id);
        var statuses=new ArrayList<>(parallel(
            () -> request("POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version)).statusCode(),
            () -> request("POST","/api/events/"+id+"/participations",f.second,Map.of("eventVersion",version)).statusCode()));
        statuses.sort(Integer::compareTo);assertEquals(List.of(201,409),statuses);assertEquals(1,occupied(id));
    }
    @Test void simultaneousDuplicateApplicationCannotCreateTwoRows()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",5),version=version(id);
        Callable<Integer> attempt=() -> request("POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version)).statusCode();
        var statuses=new ArrayList<>(parallel(attempt,attempt));statuses.sort(Integer::compareTo);assertEquals(List.of(201,409),statuses);
        assertEquals(1,db().queryForObject("select count(*) from public.event_participations where event_id=? and user_id=?",Integer.class,id,f.first.id));
    }
    @Test void shrinkingCapacityAndNewRegistrationCannotOverbook()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",2);register(id,f.first);long version=version(id);
        var statuses=parallel(
            () -> request("PATCH","/api/clubs/"+f.club+"/events/"+id,f.owner,Map.of("version",version,"capacity",1)).statusCode(),
            () -> request("POST","/api/events/"+id+"/participations",f.second,Map.of("eventVersion",version)).statusCode());
        assertTrue(statuses.equals(List.of(200,409))||statuses.equals(List.of(409,201)),"One conflicting write must be rejected");
        assertTrue(occupied(id)<=db().queryForObject("select capacity from public.events where id=?",Integer.class,id));
    }
    @Test void cancellationAndRegistrationEndWithNoActiveTicket()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",2),version=version(id);
        var statuses=parallel(
            () -> request("POST","/api/clubs/"+f.club+"/events/"+id+"/cancel",f.owner,Map.of("version",version,"reason","Closed")).statusCode(),
            () -> request("POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version)).statusCode());
        assertEquals(200,statuses.get(0));assertTrue(statuses.get(1)==201||statuses.get(1)==409);assertEquals(0,occupied(id));
    }
    @Test void membershipLeaveAndRegistrationCannotLeaveAnIneligibleMembersOnlyTicket()throws Exception {
        var f=fixture();var membership=response(201,"POST","/api/clubs/"+f.club+"/membership-requests",f.first,Map.of());
        var approved=response(200,"POST","/api/clubs/"+f.club+"/memberships/"+membership.path("id").asLong()+"/approve",f.owner,Map.of("version",membership.path("version").asLong()));
        long id=event(f,"CLUB_MEMBERS",2),version=version(id);
        var statuses=parallel(
            () -> request("POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",approved.path("version").asLong())).statusCode(),
            () -> request("POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version)).statusCode());
        assertEquals(200,statuses.get(0));assertTrue(List.of(201,404,409).contains(statuses.get(1)));assertEquals(0,occupied(id));
    }
    @Test void profileUniversityChangeAndRegistrationCannotBreakUniversityEligibility()throws Exception {
        var f=fixture();long other=university(f.admin),id=event(f,"UNIVERSITY_STUDENTS",2),version=version(id);
        long profileVersion=db().queryForObject("select version from public.student_profiles where user_id=?",Long.class,f.first.id);
        var statuses=parallel(
            () -> request("PUT","/api/me/student-profile",f.first,Map.of("universityId",other,"department","New department","version",profileVersion)).statusCode(),
            () -> request("POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version)).statusCode());
        assertTrue(statuses.equals(List.of(200,409))||statuses.equals(List.of(409,201)),"One conflicting operation must be rejected");
        assertEquals(0,db().queryForObject("select count(*) from public.event_participations p join public.events e on e.id=p.event_id "
            +"join public.clubs c on c.id=e.club_id join public.student_profiles s on s.user_id=p.user_id "
            +"where p.event_id=? and p.status='REGISTERED' and s.university_id<>c.university_id",Integer.class,id));
    }
    @Test void earlyCheckInDoesNotReleaseCapacity()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",1);var p=register(id,f.first);
        // Test-only fixture puts the event into its check-in window while registration is still open.
        Instant now=Instant.now();db().update("update public.events set registration_opens_at=?,registration_closes_at=?,starts_at=?,ends_at=?,version=version+1 where id=?",
            java.sql.Timestamp.from(now.minusSeconds(3600)),java.sql.Timestamp.from(now.plusSeconds(600)),
            java.sql.Timestamp.from(now.plusSeconds(900)),java.sql.Timestamp.from(now.plusSeconds(1800)),id);
        response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/participations/"+p.path("id").asLong()+"/check-in",f.owner,Map.of("version",p.path("version").asLong()));
        response(409,"POST","/api/events/"+id+"/participations",f.second,Map.of("eventVersion",version(id)));assertEquals(1,occupied(id));
    }
    @Test void failureAfterArchiveSideEffectsRollsBackClubEventTicketsAndRoles()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",2);register(id,f.first);
        // Stub the spy behind the transactional proxy; stubbing the proxy invokes MANDATORY outside a transaction.
        EventLifecycleService target=AopTestUtils.getUltimateTargetObject(lifecycle);clearInvocations(target);
        doAnswer(i -> {i.callRealMethod();throw new IllegalStateException("Controlled rollback fixture");})
            .when(target).clubArchived(any(Club.class),any(),any());
        try{
            long version=db().queryForObject("select version from public.clubs where id=?",Long.class,f.club);
            assertTrue(request("POST","/api/clubs/"+f.club+"/archive",f.owner,Map.of("version",version)).statusCode()>=400);
            verify(target).clubArchived(argThat(c -> c.getId()==f.club),any(),any());
            assertEquals("ACTIVE",db().queryForObject("select status from public.clubs where id=?",String.class,f.club));
            assertEquals("PUBLISHED",db().queryForObject("select status from public.events where id=?",String.class,id));assertEquals(1,occupied(id));
            assertEquals(1,db().queryForObject("select count(*) from public.user_roles where user_id=? and role='CLUB_ADMIN'",Integer.class,f.owner.id));
        }finally{doCallRealMethod().when(target).clubArchived(any(Club.class),any(),any());}
    }
    @Test void eventConstraintsAndUniqueRegistrationRollBackCleanly()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",2);var ticket=register(id,f.first);
        try(var c=source.getConnection()){
            c.setAutoCommit(false);
            try(var invalid=c.prepareStatement("update public.events set title='Must roll back',capacity=0 where id=?")){
                invalid.setLong(1,id);assertEquals("23514",assertThrows(SQLException.class,invalid::executeUpdate).getSQLState());
            }finally{c.rollback();}
            try(var duplicate=c.prepareStatement("insert into public.event_participations(event_id,user_id,registered_at,created_at,updated_at) "
                    +"select event_id,user_id,registered_at,created_at,updated_at from public.event_participations where id=?")){
                duplicate.setLong(1,ticket.path("id").asLong());assertEquals("23505",assertThrows(SQLException.class,duplicate::executeUpdate).getSQLState());
            }finally{c.rollback();}
            try(var invalid=c.prepareStatement("insert into public.event_participations(event_id,user_id,registered_at,created_at,updated_at) values(?,?,now(),now(),now())")){
                invalid.setLong(1,Long.MAX_VALUE);invalid.setLong(2,f.second.id);assertEquals("23503",assertThrows(SQLException.class,invalid::executeUpdate).getSQLState());
            }finally{c.rollback();}
        }
        assertEquals("Event fixture",db().queryForObject("select title from public.events where id=?",String.class,id));assertEquals(1,occupied(id));
    }
    @Test void discoveryAndRosterPaginationEnforcePrivacyInRealSql()throws Exception {
        var f=fixture();long hidden=event(f,"CLUB_MEMBERS",3),visible=event(f,"UNIVERSITY_STUDENTS",3);
        var page=response(200,"GET","/api/events?clubId="+f.club+"&page=0&size=1",f.first,null);
        assertEquals(1,page.path("totalElements").asLong());assertEquals(visible,page.path("content").get(0).path("id").asLong());
        Instant starts=Instant.parse(response(200,"GET","/api/events/"+visible,f.first,null).path("event").path("startsAt").asText());
        String interval="&from="+starts.minusSeconds(1)+"&to="+starts.plusSeconds(1);
        assertEquals(1,response(200,"GET","/api/events?clubId="+f.club+interval,f.first,null).path("totalElements").asLong());
        assertEquals(0,response(200,"GET","/api/events?clubId="+f.club+"&from="+starts.plusSeconds(1),f.first,null).path("totalElements").asLong());
        response(404,"GET","/api/events/"+hidden,f.first,null);
        assertEquals(2,response(200,"GET","/api/events?clubId="+f.club+"&size=1",f.admin,null).path("totalElements").asLong());
        register(visible,f.first);register(visible,f.second);
        var roster=response(200,"GET","/api/clubs/"+f.club+"/events/"+visible+"/participations?size=1",f.owner,null);
        assertEquals(2,roster.path("totalElements").asLong());assertEquals(1,roster.path("content").size());
        assertFalse(roster.path("content").get(0).has("email"));assertFalse(roster.path("content").get(0).has("bio"));
        response(403,"GET","/api/clubs/"+f.club+"/events/"+visible+"/participations",f.first,null);
        var membership=response(201,"POST","/api/clubs/"+f.club+"/membership-requests",f.first,Map.of());
        var approved=response(200,"POST","/api/clubs/"+f.club+"/memberships/"+membership.path("id").asLong()+"/approve",f.owner,Map.of("version",membership.path("version").asLong()));
        assertEquals(2,response(200,"GET","/api/events?clubId="+f.club+"&size=1",f.first,null).path("totalElements").asLong());
        response(200,"POST","/api/clubs/"+f.club+"/membership/leave",f.first,Map.of("version",approved.path("version").asLong()));
        assertEquals(1,response(200,"GET","/api/events?clubId="+f.club+"&size=1",f.first,null).path("totalElements").asLong());
    }
    @Test void completionMarksNoShowAndPreservesActualCheckIn()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",3);var attended=register(id,f.first);var absent=register(id,f.second);
        var details=response(200,"GET","/api/events/"+id,f.owner,null);
        clock.value.set(Instant.parse(details.path("event").path("startsAt").asText()).minusSeconds(1200));
        Actor owner=renew(f.owner);
        response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/participations/"+attended.path("id").asLong()+"/check-in",owner,Map.of("version",attended.path("version").asLong()));
        clock.value.set(Instant.parse(details.path("event").path("endsAt").asText()).plusSeconds(1));owner=renew(f.owner);
        var completed=response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/complete",owner,Map.of("version",version(id)));
        assertEquals("COMPLETED",completed.path("event").path("status").asText());
        assertEquals("ATTENDED",db().queryForObject("select status from public.event_participations where id=?",String.class,attended.path("id").asLong()));
        assertEquals("NO_SHOW",db().queryForObject("select status from public.event_participations where id=?",String.class,absent.path("id").asLong()));
        var archived=response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/archive",owner,Map.of("version",version(id)));
        assertEquals("COMPLETED",archived.path("event").path("status").asText());assertFalse(archived.path("event").path("archivedAt").isNull());
        response(200,"GET","/api/me/event-participations",renew(f.first),null);
    }
    @Test void removalPermissionRescheduleAndCancellationPersistConsistentStates()throws Exception {
        var f=fixture();long id=event(f,"UNIVERSITY_STUDENTS",3);var p=register(id,f.first);long pid=p.path("id").asLong();
        var removed=response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/participations/"+pid+"/remove",f.owner,Map.of("version",p.path("version").asLong(),"reason","Rule violation"));
        response(409,"POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version(id),"participationVersion",removed.path("version").asLong()));
        var allowed=response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/participations/"+pid+"/allow-reregistration",f.owner,Map.of("version",removed.path("version").asLong()));
        response(200,"POST","/api/events/"+id+"/participations",f.first,Map.of("eventVersion",version(id),"participationVersion",allowed.path("version").asLong()));
        Instant now=clock.instant();response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/reschedule",f.owner,
            Map.of("version",version(id),"registrationOpensAt",now.minusSeconds(60).toString(),"registrationClosesAt",now.plusSeconds(3600).toString(),
                "startsAt",now.plusSeconds(7200).toString(),"endsAt",now.plusSeconds(10800).toString(),"timeZone","UTC","reason","Updated date"));
        assertEquals(1,occupied(id));
        response(200,"POST","/api/clubs/"+f.club+"/events/"+id+"/cancel",f.owner,Map.of("version",version(id),"reason","Closed"));
        assertEquals("CANCELLED",db().queryForObject("select status from public.event_participations where id=?",String.class,pid));assertEquals(0,occupied(id));
    }
}
