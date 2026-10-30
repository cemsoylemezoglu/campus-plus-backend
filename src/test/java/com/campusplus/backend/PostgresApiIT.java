package com.campusplus.backend;

import com.campusplus.backend.config.JwtConfig;
import com.campusplus.backend.security.JwtTestKeys;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/server transactions; fixtures are unique and retained in campus_plus_test only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresApiIT.DatabaseConfiguration.class)
class PostgresApiIT {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.from(System::getenv).registerProperties(registry);
        String secret = JwtTestKeys.newSecret();
        registry.add("JWT_SECRET", () -> secret);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("server.address", () -> "127.0.0.1");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean DataSource dataSource() {
            return PostgresTestDatabase.from(System::getenv).writableDataSource();
        }
    }

    // Do not use a record: its generated toString would expose credentials on assertion failure.
    private static final class Actor {
        final long id;
        final String email, password, token;
        Actor(long id, String email, String password, String token) {
            this.id=id; this.email=email; this.password=password; this.token=token;
        }
    }

    private JdbcTemplate db() { return new JdbcTemplate(source); }
    private String unique() { return UUID.randomUUID().toString().replace("-", ""); }
    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer "+token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode response(int expected, String method, String path, Actor actor, Object body) throws Exception {
        var result = request(method, path, actor == null ? null : actor.token, body);
        assertEquals(expected, result.statusCode(), method+" "+path+" status");
        assertFalse(result.body().contains("password_hash"));
        assertFalse(result.body().contains("passwordHash"));
        return result.body().isBlank() ? json.nullNode() : json.readTree(result.body());
    }
    private Actor actor(boolean admin) throws Exception {
        String email="it-"+unique()+"@example.test", password=UUID.randomUUID()+"!a9";
        var registration=response(201,"POST","/api/auth/register",null,Map.of("name","Integration Student","email",email,"password",password));
        assertFalse(registration.has("password"));
        long id=registration.path("id").asLong();
        if(admin) db().update("insert into public.user_roles(user_id,role) values (?, 'ADMIN')",id);
        var login=response(200,"POST","/api/auth/login",null,Map.of("email",email,"password",password));
        assertEquals("Bearer",login.path("tokenType").asText());
        assertTrue(login.path("expiresIn").asLong()>0);
        String token=login.path("accessToken").asText();
        assertFalse(token.isBlank());
        return new Actor(id,email,password,token);
    }
    private long university(Actor admin) throws Exception {
        return response(201,"POST","/api/universities",admin,Map.of("code","IT_"+unique().substring(0,20),
                "name","Integration University","city","Ankara","countryCode","TR")).path("id").asLong();
    }
    private void profile(Actor actor,long university) throws Exception {
        response(201,"POST","/api/me/student-profile",actor,
                Map.of("universityId",university,"department","Software Engineering","studyYear",2));
    }
    private long activeClub(Actor admin,Actor owner,long university) throws Exception {
        profile(owner,university);
        var club=response(201,"POST","/api/clubs",owner,
                Map.of("name","Integration Club","slug","it-"+unique(),"description","Test fixture"));
        assertEquals("PENDING_APPROVAL",club.path("status").asText());
        long id=club.path("club").path("id").asLong();
        response(403,"POST","/api/admin/clubs/"+id+"/approve",owner,Map.of("version",club.path("version").asLong()));
        var active=response(200,"POST","/api/admin/clubs/"+id+"/approve",admin,Map.of("version",club.path("version").asLong()));
        assertEquals("ACTIVE",active.path("status").asText());
        return id;
    }
    private JsonNode apply(long club,Actor actor) throws Exception {
        return response(201,"POST","/api/clubs/"+club+"/membership-requests",actor,Map.of());
    }

    private List<Integer> parallel(Callable<Integer> first,Callable<Integer> second) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Integer>>();
            for(var action:List.of(first,second)) tasks.add(executor.submit(() -> {
                ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return action.call();
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            return List.of(tasks.get(0).get(40,TimeUnit.SECONDS),tasks.get(1).get(40,TimeUnit.SECONDS));
        }
    }

    @Test
    void registerLoginBcryptAndLegacyNullPasswords() throws Exception {
        Actor actor=actor(false);
        String hash=db().queryForObject("select password_hash from public.users where id=?",String.class,actor.id);
        assertNotEquals(actor.password,hash);
        assertTrue(passwords.matches(actor.password,hash));
        response(200,"GET","/api/users/"+actor.id,actor,null);
        response(409,"POST","/api/auth/register",null,Map.of("name","Duplicate","email",actor.email,"password",actor.password));
        var wrong=response(401,"POST","/api/auth/login",null,Map.of("email",actor.email,"password","incorrect-password"));
        String legacyEmail="legacy-"+unique()+"@example.test";
        Long legacy=db().queryForObject("insert into public.users(name,email) values('Legacy fixture',?) returning id",Long.class,legacyEmail);
        db().update("insert into public.user_roles(user_id,role) values(?, 'STUDENT')",legacy);
        var old=response(401,"POST","/api/auth/login",null,Map.of("email",legacyEmail,"password","incorrect-password"));
        var unknown=response(401,"POST","/api/auth/login",null,Map.of("email","unknown-"+unique()+"@example.test","password","incorrect-password"));
        assertEquals(wrong.path("message").asText(),old.path("message").asText());
        assertEquals(wrong.path("message").asText(),unknown.path("message").asText());
        assertNull(db().queryForObject("select password_hash from public.users where id=?",String.class,legacy));
    }

    @Test
    void jwtExpiryInvalidTokensAndRbacUse401And403() throws Exception {
        Actor actor=actor(false);
        response(401,"GET","/api/users/"+actor.id,null,null);
        assertEquals(401,request("GET","/api/users/"+actor.id,"invalid.token.value",null).statusCode());
        Instant past=Instant.now().minusSeconds(300);
        var claims=JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject(Long.toString(actor.id)).issuedAt(past).expiresAt(past.plusSeconds(30)).build();
        String expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertEquals(401,request("GET","/api/users/"+actor.id,expired,null).statusCode());
        response(403,"GET","/api/users",actor,null);
        response(200,"GET","/api/users",actor(true),null);
    }

    @Test
    void universityAndProfileRespectRolesAndUserOwnership() throws Exception {
        Actor admin=actor(true),student=actor(false),other=actor(false);
        long university=university(admin);
        response(403,"POST","/api/universities",student,Map.of("code","DENIED","name","Denied","city","Ankara","countryCode","TR"));
        profile(student,university);
        assertEquals(university,response(200,"GET","/api/me/student-profile",student,null).path("university").path("id").asLong());
        response(409,"POST","/api/me/student-profile",student,Map.of("universityId",university,"department","Duplicate"));
        response(403,"GET","/api/admin/users/"+student.id+"/student-profile",other,null);
        response(403,"PUT","/api/users/"+student.id,other,Map.of("name","Unauthorized change","email",student.email));
        assertEquals("Integration Student",db().queryForObject("select name from public.users where id=?",String.class,student.id));
        response(200,"GET","/api/admin/users/"+student.id+"/student-profile",admin,null);
    }

    @Test
    void clubLocalManagementAndUniversityBoundariesRemainEnforced() throws Exception {
        Actor admin=actor(true),owner=actor(false),otherOwner=actor(false),foreign=actor(false);
        long university=university(admin),club=activeClub(admin,owner,university);
        activeClub(admin,otherOwner,university); // Has global CLUB_ADMIN through another club.
        response(403,"GET","/api/clubs/"+club+"/memberships",otherOwner,null);
        profile(foreign,university(admin));
        // Existing API contract: eligibility mismatch is a 409 domain conflict.
        response(409,"POST","/api/clubs/"+club+"/membership-requests",foreign,Map.of());
        assertEquals(0,db().queryForObject("select count(*) from public.club_memberships where club_id=? and user_id=?",Integer.class,club,foreign.id));
        response(200,"GET","/api/clubs/"+club+"/memberships",owner,null);
    }

    @Test
    void simultaneousMembershipApplicationsCreateOneRow() throws Exception {
        Actor admin=actor(true),owner=actor(false),student=actor(false);
        long university=university(admin),club=activeClub(admin,owner,university);
        profile(student,university);
        Callable<Integer> attempt=() -> request("POST","/api/clubs/"+club+"/membership-requests",student.token,Map.of()).statusCode();
        var statuses=new ArrayList<>(parallel(attempt,attempt));
        statuses.sort(Integer::compareTo);assertEquals(List.of(201,409),statuses);
        assertEquals(1,db().queryForObject("select count(*) from public.club_memberships where club_id=? and user_id=?",Integer.class,club,student.id));
    }

    @Test
    void universityChangeAndMembershipApplicationCannotCreateCrossUniversityMembership() throws Exception {
        Actor admin=actor(true),owner=actor(false),student=actor(false);
        long university=university(admin),otherUniversity=university(admin),club=activeClub(admin,owner,university);
        profile(student,university);
        long version=response(200,"GET","/api/me/student-profile",student,null).path("version").asLong();
        var statuses=parallel(
                () -> request("POST","/api/clubs/"+club+"/membership-requests",student.token,Map.of()).statusCode(),
                () -> request("PUT","/api/me/student-profile",student.token,
                        Map.of("universityId",otherUniversity,"department","New Department","version",version)).statusCode());
        assertTrue(statuses.equals(List.of(201,409)) || statuses.equals(List.of(409,200)),"One conflicting operation must be rejected");
        assertEquals(0,db().queryForObject("select count(*) from public.club_memberships m join public.clubs c on c.id=m.club_id "
                +"join public.student_profiles p on p.user_id=m.user_id where m.user_id=? and m.status in ('PENDING','ACTIVE') "
                +"and p.university_id<>c.university_id",Integer.class,student.id));
    }

    @Test
    void competingOwnershipTransfersLeaveExactlyOneOwner() throws Exception {
        Actor admin=actor(true),owner=actor(false),first=actor(false),second=actor(false);
        long university=university(admin),club=activeClub(admin,owner,university);
        var targets=new ArrayList<JsonNode>();
        for(Actor member:List.of(first,second)) {
            profile(member,university);var pending=apply(club,member);
            targets.add(response(200,"POST","/api/clubs/"+club+"/memberships/"+pending.path("id").asLong()+"/approve",owner,
                    Map.of("version",pending.path("version").asLong())));
        }
        long clubVersion=response(200,"GET","/api/clubs/"+club,owner,null).path("version").asLong();
        long ownerVersion=db().queryForObject("select version from public.club_memberships where club_id=? and user_id=?",Long.class,club,owner.id);
        var attempts=new ArrayList<Callable<Integer>>();
        for(JsonNode target:targets) {
            var body=Map.of("targetMembershipId",target.path("id").asLong(),"clubVersion",clubVersion,
                    "ownerMembershipVersion",ownerVersion,"targetMembershipVersion",target.path("version").asLong());
            attempts.add(() -> request("POST","/api/clubs/"+club+"/ownership-transfer",owner.token,body).statusCode());
        }
        var statuses=new ArrayList<>(parallel(attempts.get(0),attempts.get(1)));
        statuses.sort(Integer::compareTo);
        assertTrue(statuses.equals(List.of(200,403)) || statuses.equals(List.of(200,409)),"Only one ownership transfer may succeed");
        assertEquals(1,db().queryForObject("select count(*) from public.club_memberships where club_id=? and role='OWNER' and status='ACTIVE'",Integer.class,club));
        long winner=db().queryForObject("select user_id from public.club_memberships where club_id=? and role='OWNER' and status='ACTIVE'",Long.class,club);
        assertTrue(winner==first.id || winner==second.id);
    }

    @Test
    void ownerMustTransferAndDatabaseEnforcesSingleOwner() throws Exception {
        Actor admin=actor(true),owner=actor(false),member=actor(false);
        long university=university(admin),club=activeClub(admin,owner,university);
        profile(member,university);JsonNode pending=apply(club,member);
        long membership=pending.path("id").asLong();
        JsonNode approved=response(200,"POST","/api/clubs/"+club+"/memberships/"+membership+"/approve",owner,
                Map.of("version",pending.path("version").asLong()));
        long ownerMembership=db().queryForObject("select id from public.club_memberships where club_id=? and user_id=?",Long.class,club,owner.id);
        long ownerVersion=db().queryForObject("select version from public.club_memberships where id=?",Long.class,ownerMembership);
        response(409,"POST","/api/clubs/"+club+"/membership/leave",owner,Map.of("version",ownerVersion));
        try(var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try(var update=connection.prepareStatement("update public.club_memberships set role='OWNER' where id=?")) {
                update.setLong(1,membership);
                var error=assertThrows(SQLException.class,update::executeUpdate);
                assertEquals("23505",error.getSQLState());
            } finally { connection.rollback(); }
        }
        long clubVersion=response(200,"GET","/api/clubs/"+club,owner,null).path("version").asLong();
        response(200,"POST","/api/clubs/"+club+"/ownership-transfer",owner,Map.of("targetMembershipId",membership,
                "clubVersion",clubVersion,"ownerMembershipVersion",ownerVersion,"targetMembershipVersion",approved.path("version").asLong()));
        assertEquals(member.id,db().queryForObject("select user_id from public.club_memberships where club_id=? and role='OWNER' and status='ACTIVE'",Long.class,club));
        assertEquals(1,db().queryForObject("select count(*) from public.club_memberships where club_id=? and role='OWNER' and status='ACTIVE'",Integer.class,club));
        long after=db().queryForObject("select version from public.club_memberships where id=?",Long.class,ownerMembership);
        response(200,"POST","/api/clubs/"+club+"/membership/leave",owner,Map.of("version",after));
    }

    @Test
    void foreignKeyAndCheckFailuresRollBackTheWholeTransaction() throws Exception {
        Actor actor=actor(false);
        try(var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try(var edit=connection.prepareStatement("update public.users set name='Must roll back' where id=?");
                var invalid=connection.prepareStatement("insert into public.user_roles(user_id,role) values(?, 'STUDENT')")) {
                edit.setLong(1,actor.id);edit.executeUpdate();
                invalid.setLong(1,Long.MAX_VALUE);
                assertEquals("23503",assertThrows(SQLException.class,invalid::executeUpdate).getSQLState());
            } finally { connection.rollback(); }
            try(var invalid=connection.prepareStatement("insert into public.user_roles(user_id,role) values(?, 'UNEXPECTED')")) {
                invalid.setLong(1,actor.id);
                assertEquals("23514",assertThrows(SQLException.class,invalid::executeUpdate).getSQLState());
            } finally { connection.rollback(); }
        }
        assertEquals("Integration Student",db().queryForObject("select name from public.users where id=?",String.class,actor.id));
    }
}
