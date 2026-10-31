package com.campusplus.backend.event;

import com.campusplus.backend.config.SecurityConfig;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.*;
import com.campusplus.backend.service.event.*;
import jakarta.persistence.*;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real JWT/MVC/method-security/domain services; no JDBC, DDL or real concurrency claims. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventModulesTest {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    static class TestClock extends Clock {
        Instant value=NOW;
        public Instant instant(){return value;}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,CurrentActor.class,ClubPolicy.class,ClubMembershipPolicy.class,ClubWorkspace.class,
        ClubSettings.class,ClubService.class,ClubMembershipService.class,ClubQueryService.class,
        ClubController.class,ClubMembershipController.class,StudentProfilePolicy.class,
        EventPolicy.class,EventSettings.class,EventWorkspace.class,EventLifecycleService.class,EventService.class,
        EventParticipationService.class,EventQueryService.class,EventController.class,EventParticipationController.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean com.campusplus.backend.service.notification.NotificationWriter notificationWriter(){return mock(com.campusplus.backend.service.notification.NotificationWriter.class);}
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean ClubRepository clubs(){return mock(ClubRepository.class);}
        @Bean ClubMembershipRepository memberships(){return mock(ClubMembershipRepository.class);}
        @Bean StudentProfileRepository profiles(){return mock(StudentProfileRepository.class);}
        @Bean UniversityRepository universities(){return mock(UniversityRepository.class);}
        @Bean EventRepository events(){return mock(EventRepository.class);}
        @Bean EventParticipationRepository participants(){return mock(EventParticipationRepository.class);}
        @Bean com.campusplus.backend.service.announcement.AnnouncementLifecycleService announcementLifecycle() {
            return mock(com.campusplus.backend.service.announcement.AnnouncementLifecycleService.class);
        }
        @Bean EntityManager entityManager(){return mock(EntityManager.class);}
        @Bean @Primary TestClock clock(){return new TestClock();}
        @Bean static MethodValidationPostProcessor validation(){var p=new MethodValidationPostProcessor();p.setProxyTargetClass(true);return p;}
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    JwtEncoder encoder;
    UserRepository users;ClubRepository clubs;ClubMembershipRepository memberships;
    StudentProfileRepository profiles;UniversityRepository universities;
    EventRepository events;EventParticipationRepository participants;EntityManager em;TestClock clock;
    final Map<Long,User> people=new HashMap<>();final Map<Long,StudentProfile> academics=new HashMap<>();
    final Map<Long,Club> catalog=new HashMap<>();final Map<Long,ClubMembership> members=new HashMap<>();
    final Map<Long,Event> calendar=new HashMap<>();final Map<Long,EventParticipation> tickets=new HashMap<>();
    final JsonMapper json=JsonMapper.builder().build();
    University university,other;
    static void field(Object value,String name,Object data){ReflectionTestUtils.setField(value,name,data);}

    @BeforeAll void start(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"JWT_SECRET="+JwtTestKeys.newSecret());
        context.register(Config.class);context.refresh();encoder=context.getBean(JwtEncoder.class);
        users=context.getBean(UserRepository.class);clubs=context.getBean(ClubRepository.class);memberships=context.getBean(ClubMembershipRepository.class);
        profiles=context.getBean(StudentProfileRepository.class);universities=context.getBean(UniversityRepository.class);
        events=context.getBean(EventRepository.class);participants=context.getBean(EventParticipationRepository.class);
        em=context.getBean(EntityManager.class);clock=context.getBean(TestClock.class);
        mvc=webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    private Event event(long id,long clubId,EventAudience audience,EventStatus status){
        var e=new Event(catalog.get(clubId),people.get(clubId==10?2L:6L),"Event "+id,"Description","Venue",null,audience,10,
                NOW.minusSeconds(60),NOW.plusSeconds(7200),NOW.plusSeconds(7200),NOW.plusSeconds(10800),"Europe/Istanbul",NOW.minusSeconds(120));
        field(e,"id",id);field(e,"version",0L);if(status==EventStatus.PUBLISHED)e.publish(NOW.minusSeconds(30));
        calendar.put(id,e);return e;
    }
    private EventParticipation ticket(long id,long eventId,long userId){
        var p=new EventParticipation(calendar.get(eventId),academics.get(userId),NOW.minusSeconds(10));
        field(p,"id",id);field(p,"version",0L);tickets.put(id,p);return p;
    }
    private boolean activeMember(Long clubId,Long userId){return members.values().stream().anyMatch(m ->
        m.getClub().getId().equals(clubId)&&m.getUserId().equals(userId)&&m.getStatus()==ClubMembershipStatus.ACTIVE);}
    private long seats(Long eventId){return tickets.values().stream().filter(p -> p.getEvent().getId().equals(eventId)
        && EventWorkspace.OCCUPIED.contains(p.getStatus())).count();}
    private <T> Page<T> page(List<T> values,Pageable pageable){int start=(int)Math.min(values.size(),pageable.getOffset());
        return new PageImpl<>(values.subList(start,Math.min(values.size(),start+pageable.getPageSize())),pageable,values.size());}

    @BeforeEach void fixtures(){
        reset(users,clubs,memberships,profiles,universities,events,participants,em);clock.value=NOW;
        people.clear();academics.clear();catalog.clear();members.clear();calendar.clear();tickets.clear();
        university=new University("UNI","University","City","TR",null,NOW);field(university,"id",1L);
        other=new University("OTHER","Other","City","TR",null,NOW);field(other,"id",2L);
        for(long id=2;id<=8;id++){
            User u=new User("Person "+id,"person"+id+"@example.test");field(u,"id",id);u.setPasswordHash("synthetic-hash");
            if(id==2||id==3||id==6)u.grantRole(Role.CLUB_ADMIN);if(id==5)u.grantRole(Role.ADMIN);people.put(id,u);
            var profile=new StudentProfile(u,id==7?other:university,"Department",(short)2,"private bio",NOW);
            field(profile,"version",0L);academics.put(id,profile);
        }
        for(long id:new long[]{10,20}){
            var c=new Club(university,people.get(id==10?2L:6L),"Club "+id,"club-"+id,"Club description",null,NOW);
            field(c,"id",id);field(c,"version",0L);field(c,"status",ClubStatus.ACTIVE);catalog.put(id,c);
        }
        for(long id:new long[]{2,3,4,6}){
            long clubId=id==6?20:10;var m=new ClubMembership(catalog.get(clubId),academics.get(id),id==2||id==6,NOW);
            field(m,"id",id+100);field(m,"version",0L);field(m,"status",ClubMembershipStatus.ACTIVE);field(m,"joinedAt",NOW.minusSeconds(100));
            if(id==3)field(m,"role",ClubMembershipRole.MANAGER);members.put(id+100,m);
        }
        event(30,10,EventAudience.UNIVERSITY_STUDENTS,EventStatus.PUBLISHED);
        event(31,10,EventAudience.CLUB_MEMBERS,EventStatus.PUBLISHED);
        event(32,10,EventAudience.UNIVERSITY_STUDENTS,EventStatus.DRAFT);
        event(40,20,EventAudience.UNIVERSITY_STUDENTS,EventStatus.PUBLISHED);
        ticket(1000,30,4);ticket(1001,31,4);
        when(users.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profiles.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(academics.get(i.<Long>getArgument(0))));
        when(universities.findByIdForShare(anyLong())).thenAnswer(i -> Optional.of(i.<Long>getArgument(0)==1?university:other));
        when(clubs.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.findByIdForShare(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(memberships.findByClubIdAndUserId(anyLong(),anyLong())).thenAnswer(i -> members.values().stream().filter(m ->
            m.getClub().getId().equals(i.getArgument(0))&&m.getUserId().equals(i.getArgument(1))).findFirst());
        when(memberships.findByIdAndClubId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(members.get(i.<Long>getArgument(0))).filter(m -> m.getClub().getId().equals(i.getArgument(1))));
        when(memberships.saveAndFlush(any())).thenAnswer(i -> {ClubMembership m=i.getArgument(0);field(m,"version",m.getVersion()+1);return m;});
        when(memberships.activeManagerUserIds(anyLong())).thenAnswer(i -> members.values().stream().filter(m ->
            m.getClub().getId().equals(i.getArgument(0))&&m.getStatus()==ClubMembershipStatus.ACTIVE&&m.getRole()!=ClubMembershipRole.MEMBER).map(ClubMembership::getUserId).sorted().toList());
        when(memberships.hasManagement(anyLong())).thenAnswer(i -> members.values().stream().anyMatch(m ->
            m.getUserId().equals(i.getArgument(0))&&m.getStatus()==ClubMembershipStatus.ACTIVE&&m.getRole()!=ClubMembershipRole.MEMBER&&m.getClub().getStatus()!=ClubStatus.ARCHIVED));
        when(events.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(calendar.get(i.<Long>getArgument(0))));
        when(events.findByIdAndClubId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(calendar.get(i.<Long>getArgument(0))).filter(e -> e.getClub().getId().equals(i.getArgument(1))));
        when(events.findForUpdate(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(calendar.get(i.<Long>getArgument(0))).filter(e -> e.getClub().getId().equals(i.getArgument(1))));
        AtomicLong nextEvent=new AtomicLong(50),nextPart=new AtomicLong(2000);
        when(events.saveAndFlush(any())).thenAnswer(i -> {Event e=i.getArgument(0);if(e.getId()==null){field(e,"id",nextEvent.getAndIncrement());field(e,"version",0L);}else field(e,"version",e.getVersion()+1);calendar.put(e.getId(),e);return e;});
        when(participants.saveAndFlush(any())).thenAnswer(i -> {EventParticipation p=i.getArgument(0);if(p.getId()==null){field(p,"id",nextPart.getAndIncrement());field(p,"version",0L);}else field(p,"version",p.getVersion()+1);tickets.put(p.getId(),p);return p;});
        when(participants.findByEventIdAndUserId(anyLong(),anyLong())).thenAnswer(i -> tickets.values().stream().filter(p ->
            p.getEvent().getId().equals(i.getArgument(0))&&p.getUserId().equals(i.getArgument(1))).findFirst());
        when(participants.findByIdAndEventId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(tickets.get(i.<Long>getArgument(0))).filter(p -> p.getEvent().getId().equals(i.getArgument(1))));
        when(participants.countByEventIdAndStatusIn(anyLong(),anyCollection())).thenAnswer(i -> seats(i.getArgument(0)));
        when(participants.existsByEventIdAndStatus(anyLong(),any())).thenAnswer(i -> tickets.values().stream().anyMatch(p -> p.getEvent().getId().equals(i.getArgument(0))&&p.getStatus()==i.getArgument(1)));
        when(participants.endRegistered(anyLong(),nullable(Long.class),any(),any(),any(),nullable(String.class),any())).thenAnswer(i -> {
            int count=0;for(var p:tickets.values())if(p.getEvent().getId().equals(i.getArgument(0))&&(i.getArgument(1)==null||p.getUserId().equals(i.getArgument(1)))&&p.getStatus()==EventParticipationStatus.REGISTERED){
                p.end(i.getArgument(2),i.getArgument(3),i.getArgument(4),i.getArgument(5),i.getArgument(6));field(p,"version",p.getVersion()+1);count++;}return count;
        });
        when(events.lockOpenForClub(anyLong(),anyCollection())).thenAnswer(i -> calendar.values().stream().filter(e -> e.getClub().getId().equals(i.getArgument(0))&&i.<Collection<EventStatus>>getArgument(1).contains(e.getStatus())).sorted(Comparator.comparing(Event::getId)).toList());
        when(events.lockMemberEvents(anyLong(),anyLong(),any())).thenAnswer(i -> calendar.values().stream().filter(e -> e.getClub().getId().equals(i.getArgument(0))&&e.getStatus()==EventStatus.PUBLISHED&&e.getAudience()==EventAudience.CLUB_MEMBERS&&e.getEndsAt().isAfter(i.getArgument(2))&&tickets.values().stream().anyMatch(p -> p.getEvent()==e&&p.getUserId().equals(i.getArgument(1))&&p.getStatus()==EventParticipationStatus.REGISTERED)).sorted(Comparator.comparing(Event::getId)).toList());
        when(participants.blocksUniversityChange(anyLong(),any())).thenAnswer(i -> tickets.values().stream().anyMatch(p -> p.getUserId().equals(i.getArgument(0))&&EventWorkspace.OCCUPIED.contains(p.getStatus())&&p.getEvent().getStatus()==EventStatus.PUBLISHED&&p.getEvent().getEndsAt().isAfter(i.getArgument(1))));
        when(participants.own(anyLong(),nullable(EventParticipationStatus.class),any())).thenAnswer(i -> page(tickets.values().stream().filter(p -> p.getUserId().equals(i.getArgument(0))&&(i.getArgument(1)==null||p.getStatus()==i.getArgument(1))).sorted(Comparator.comparing(EventParticipation::getId)).toList(),i.getArgument(2)));
        when(participants.managed(anyLong(),nullable(EventParticipationStatus.class),any())).thenAnswer(i -> page(tickets.values().stream().filter(p -> p.getEvent().getId().equals(i.getArgument(0))&&(i.getArgument(1)==null||p.getStatus()==i.getArgument(1))).map(p -> ManagedEventParticipationResponse.from(p,people.get(p.getUserId()))).toList(),i.getArgument(2)));
        when(participants.counts(anyCollection(),anyCollection())).thenAnswer(i -> i.<Collection<Long>>getArgument(0).stream().map(id -> new EventParticipationRepository.SeatCount(){public Long getEventId(){return id;}public long getAmount(){return seats(id);}}).toList());
        when(events.discover(nullable(Long.class),nullable(Long.class),nullable(Instant.class),nullable(Instant.class),anyString(),anyBoolean(),anyLong(),any())).thenAnswer(i -> page(calendar.values().stream().filter(e -> e.getStatus()==EventStatus.PUBLISHED&&e.getArchivedAt()==null&&e.getClub().getStatus()==ClubStatus.ACTIVE&&(e.getAudience()==EventAudience.UNIVERSITY_STUDENTS||i.<Boolean>getArgument(5)||activeMember(e.getClub().getId(),i.getArgument(6)))).sorted(Comparator.comparing(Event::getId)).toList(),i.getArgument(7)));
    }
    String token(long id){Instant now=clock.instant();return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
        JwtClaimsSet.builder().issuer(com.campusplus.backend.config.JwtConfig.ISSUER).audience(List.of(com.campusplus.backend.config.JwtConfig.AUDIENCE))
        .subject(Long.toString(id)).issuedAt(now).expiresAt(now.plusSeconds(900)).build())).getTokenValue();}
    String body(Object... pairs){var values=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)values.put((String)pairs[i],pairs[i+1]);return json.writeValueAsString(values);}
    String createBody(){return body("title","New Event","description","Description","venue","Hall","capacity",2,"registrationOpensAt",NOW.toString(),
        "registrationClosesAt",NOW.plusSeconds(3600).toString(),"startsAt",NOW.plusSeconds(7200).toString(),"endsAt",NOW.plusSeconds(10800).toString(),"timeZone","Europe/Istanbul");}
    org.springframework.test.web.servlet.ResultActions postAs(String path,long actor,String content)throws Exception{return mvc.perform(post(path).header("Authorization",token(actor)).contentType("application/json").content(content));}
    org.springframework.test.web.servlet.ResultActions action(String path,long actor,long version)throws Exception{return postAs(path,actor,body("version",version));}

    @Test void anonymousIs401AndWrongClubManagerIs403()throws Exception{
        mvc.perform(get("/api/events/30")).andExpect(status().isUnauthorized());
        postAs("/api/clubs/10/events",6,createBody()).andExpect(status().isForbidden());
        postAs("/api/clubs/10/events",4,createBody()).andExpect(status().isForbidden());verify(events,never()).saveAndFlush(any());
    }
    @Test void ownerAndManagerCreateDraftWithSafeDtoAndServerOwnedIdentity()throws Exception{
        postAs("/api/clubs/10/events",3,createBody().replace("\"capacity\":2","\"capacity\":2,\"status\":\"PUBLISHED\",\"clubId\":20,\"createdBy\":6"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.event.status").value("DRAFT")).andExpect(jsonPath("$.event.club.id").value(10))
            .andExpect(jsonPath("$.createdBy").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void privateEventAndDraftAre404ForNonMembersButReadableByManagers()throws Exception{
        mvc.perform(get("/api/events/31").header("Authorization",token(8))).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/32").header("Authorization",token(4))).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/32").header("Authorization",token(3))).andExpect(status().isOk());
    }
    @Test void discoveryFiltersPrivateEventsBeforePaginationAndUsesCurrentActor()throws Exception{
        mvc.perform(get("/api/events").header("Authorization",token(8))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        verify(events).discover(isNull(),isNull(),isNull(),isNull(),eq("%%"),eq(false),eq(8L),any());
    }
    @Test void publishRequiresVersionActiveClubAndFutureWindow()throws Exception{
        action("/api/clubs/10/events/32/publish",2,99).andExpect(status().isConflict());
        action("/api/clubs/10/events/32/publish",2,0).andExpect(status().isOk()).andExpect(jsonPath("$.event.status").value("PUBLISHED"));
        action("/api/clubs/10/events/32/publish",2,1).andExpect(status().isConflict());
    }
    @Test void parentMismatchIs404AndUnauthorizedManagerCannotReadRoster()throws Exception{
        action("/api/clubs/10/events/40/publish",2,0).andExpect(status().isNotFound());
        mvc.perform(get("/api/clubs/10/events/30/participations").header("Authorization",token(6))).andExpect(status().isForbidden());
        action("/api/clubs/20/events/40/participations/1000/check-in",6,0).andExpect(status().isNotFound());
    }
    @Test void registerUsesOwnProfileAndRejectsForeignUniversityAndMissingProfile()throws Exception{
        postAs("/api/events/30/participations",8,body("eventVersion",0,"userId",7,"status","ATTENDED"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REGISTERED"));
        assertTrue(tickets.values().stream().anyMatch(p -> p.getEvent().getId()==30&&p.getUserId()==8));
        postAs("/api/events/30/participations",7,body("eventVersion",0)).andExpect(status().isConflict());
        academics.remove(5L);postAs("/api/events/30/participations",5,body("eventVersion",0)).andExpect(status().isConflict());
    }
    @Test void duplicateAndStaleVersionRegistrationAre409()throws Exception{
        postAs("/api/events/30/participations",4,body("eventVersion",0)).andExpect(status().isConflict());
        postAs("/api/events/30/participations",8,body("eventVersion",99)).andExpect(status().isConflict());
    }
    @Test void membersOnlyEnrollmentCannotBeBypassedEvenByPlatformAdmin()throws Exception{
        postAs("/api/events/31/participations",8,body("eventVersion",0)).andExpect(status().isNotFound());
        postAs("/api/events/31/participations",5,body("eventVersion",0)).andExpect(status().isConflict());
    }
    @Test void unpublishedAudienceCanChangeButPublishedAudienceCannot()throws Exception{
        mvc.perform(patch("/api/clubs/10/events/32").header("Authorization",token(2)).contentType("application/json")
            .content(body("version",0,"audience","CLUB_MEMBERS"))).andExpect(status().isOk()).andExpect(jsonPath("$.event.audience").value("CLUB_MEMBERS"));
        mvc.perform(patch("/api/clubs/10/events/30").header("Authorization",token(2)).contentType("application/json")
            .content(body("version",0,"audience","CLUB_MEMBERS"))).andExpect(status().isConflict());
    }
    @Test void eventCreatorHasNoPrivilegeAfterLosingLocalMembership()throws Exception{
        field(members.get(102L),"status",ClubMembershipStatus.LEFT);
        action("/api/clubs/10/events/32/publish",2,0).andExpect(status().isForbidden());
    }
    @Test void windowIsCheckedAfterWaitingForEventLock()throws Exception{
        doAnswer(i -> {clock.value=calendar.get(30L).getRegistrationClosesAt();return null;}).when(em).refresh(calendar.get(30L),LockModeType.PESSIMISTIC_WRITE);
        postAs("/api/events/30/participations",8,body("eventVersion",0)).andExpect(status().isConflict());
        verify(participants,never()).saveAndFlush(any());
    }
    @Test void inactiveUniversityOrClubBlocksCreationAndEnrollment()throws Exception{
        university.update("University","City",null,false,NOW);
        postAs("/api/clubs/10/events",3,createBody()).andExpect(status().isConflict());
        postAs("/api/events/30/participations",8,body("eventVersion",0)).andExpect(status().isConflict());
    }
    @Test void absentOrExpiredJwtCannotUseEventApis()throws Exception{
        String expired="Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer(com.campusplus.backend.config.JwtConfig.ISSUER).audience(List.of(com.campusplus.backend.config.JwtConfig.AUDIENCE))
            .subject("4").issuedAt(NOW.minusSeconds(100)).expiresAt(NOW.minusSeconds(1)).build())).getTokenValue();
        mvc.perform(get("/api/events/30").header("Authorization",expired)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events/30").header("Authorization","Bearer invalid.token.value")).andExpect(status().isUnauthorized());
    }
    @Test void fullCapacityAndEarlyCheckInDoNotFreeASeat()throws Exception{
        field(calendar.get(30L),"capacity",1);clock.value=NOW.plusSeconds(5400);
        action("/api/clubs/10/events/30/participations/1000/check-in",3,0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATTENDED"));
        postAs("/api/events/30/participations",8,body("eventVersion",0)).andExpect(status().isConflict());assertEquals(1,seats(30L));
    }
    @Test void cancellationAndVersionedReregistrationReuseSameRow()throws Exception{
        action("/api/events/30/participation/cancel",4,0).andExpect(status().isOk());
        postAs("/api/events/30/participations",4,body("eventVersion",0,"participationVersion",0)).andExpect(status().isConflict());
        postAs("/api/events/30/participations",4,body("eventVersion",0,"participationVersion",1)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1000));
        assertEquals(2,tickets.size());
    }
    @Test void suspendedClubDoesNotPreventOwnCancellationButBlocksNewRegistration()throws Exception{
        ticket(1002,30,8);field(catalog.get(10L),"status",ClubStatus.SUSPENDED);
        action("/api/events/30/participation/cancel",8,0).andExpect(status().isOk());
        postAs("/api/events/30/participations",4,body("eventVersion",0)).andExpect(status().isConflict());
    }
    @Test void cannotCancelAttendanceOrCancelAfterStart()throws Exception{
        clock.value=NOW.plusSeconds(7200);action("/api/events/30/participation/cancel",4,0).andExpect(status().isConflict());
        tickets.get(1000L).checkIn(people.get(3L),clock.instant());action("/api/events/30/participation/cancel",4,0).andExpect(status().isConflict());
    }
    @Test void removedParticipantNeedsExplicitPermissionBeforeReregistration()throws Exception{
        postAs("/api/clubs/10/events/30/participations/1000/remove",3,body("version",0,"reason","Rule violation")).andExpect(status().isOk());
        postAs("/api/events/30/participations",4,body("eventVersion",0,"participationVersion",1)).andExpect(status().isConflict());
        action("/api/clubs/10/events/30/participations/1000/allow-reregistration",3,1).andExpect(status().isOk());
        postAs("/api/events/30/participations",4,body("eventVersion",0,"participationVersion",2)).andExpect(status().isOk());
    }
    @Test void onlyManagersCanCheckInAndWindowAndEligibilityAreChecked()throws Exception{
        action("/api/clubs/10/events/30/participations/1000/check-in",4,0).andExpect(status().isForbidden());
        action("/api/clubs/10/events/30/participations/1000/check-in",3,0).andExpect(status().isConflict());
        clock.value=NOW.plusSeconds(5400);field(academics.get(4L),"university",other);
        action("/api/clubs/10/events/30/participations/1000/check-in",3,0).andExpect(status().isConflict());
    }
    @Test void reducingCapacityBelowOccupiedSeatsIs409AndUnlimitedIsExplicit()throws Exception{
        ticket(1002,30,8);
        mvc.perform(patch("/api/clubs/10/events/30").header("Authorization",token(2)).contentType("application/json").content(body("version",0,"capacity",1))).andExpect(status().isConflict());
        mvc.perform(patch("/api/clubs/10/events/30").header("Authorization",token(2)).contentType("application/json").content(body("version",0,"unlimited",true)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.event.capacity").isEmpty());
    }
    @Test void publishedRescheduleNeedsReasonAndCannotFollowCheckIn()throws Exception{
        String schedule=body("version",0,"registrationOpensAt",NOW.toString(),"registrationClosesAt",NOW.plusSeconds(9000).toString(),
            "startsAt",NOW.plusSeconds(10000).toString(),"endsAt",NOW.plusSeconds(12000).toString(),"timeZone","UTC");
        postAs("/api/clubs/10/events/30/reschedule",2,schedule).andExpect(status().isBadRequest());
        postAs("/api/clubs/10/events/30/reschedule",2,schedule.replace("\"version\":0","\"version\":0,\"reason\":\"New venue date\""))
            .andExpect(status().isOk()).andExpect(jsonPath("$.event.version").value(1));
        assertEquals(EventParticipationStatus.REGISTERED,tickets.get(1000L).getStatus());
        tickets.get(1000L).checkIn(people.get(3L),NOW);
        postAs("/api/clubs/10/events/30/reschedule",2,schedule.replace("\"version\":0","\"version\":1,\"reason\":\"Later\""))
            .andExpect(status().isConflict());
    }
    @Test void cancelAndCompleteFinalizeRegisteredTicketsAndPreserveAttendance()throws Exception{
        var attended=ticket(1002,30,8);attended.checkIn(people.get(3L),NOW);
        postAs("/api/clubs/10/events/30/cancel",2,body("version",0,"reason","Weather"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.event.status").value("CANCELLED"));
        assertEquals(EventParticipationStatus.CANCELLED,tickets.get(1000L).getStatus());assertEquals(EventParticipationStatus.ATTENDED,attended.getStatus());
        action("/api/clubs/10/events/31/complete",2,0).andExpect(status().isConflict());
        clock.value=NOW.plusSeconds(10800);action("/api/clubs/10/events/31/complete",2,0).andExpect(status().isOk());
        assertEquals(EventParticipationStatus.NO_SHOW,tickets.get(1001L).getStatus());
    }
    @Test void archiveRequiresTerminalStateAndAdminCanCancelInSuspendedClub()throws Exception{
        action("/api/clubs/10/events/30/archive",2,0).andExpect(status().isConflict());field(catalog.get(10L),"status",ClubStatus.SUSPENDED);
        postAs("/api/clubs/10/events/30/cancel",2,body("version",0,"reason","Closed")).andExpect(status().isForbidden());
        postAs("/api/clubs/10/events/30/cancel",5,body("version",0,"reason","Closed")).andExpect(status().isOk());
        action("/api/clubs/10/events/30/archive",5,1).andExpect(status().isOk());assertEquals(EventStatus.CANCELLED,calendar.get(30L).getStatus());
    }
    @Test void ownHistoryNeverExposesPrivateEventDetailsOrOtherPeople()throws Exception{
        mvc.perform(get("/api/me/event-participations").header("Authorization",token(4))).andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.content[0].event.description").doesNotExist())
            .andExpect(jsonPath("$.content[0].event.venue").doesNotExist()).andExpect(jsonPath("$.content[0].email").doesNotExist());
        mvc.perform(get("/api/clubs/10/events/30/participations").header("Authorization",token(3))).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].email").doesNotExist()).andExpect(jsonPath("$.content[0].bio").doesNotExist());
    }
    @Test void membershipLeaveCancelsOnlyMembersOnlyUpcomingTickets()throws Exception{
        action("/api/clubs/10/membership/leave",4,0).andExpect(status().isOk());
        assertEquals(EventParticipationStatus.CANCELLED,tickets.get(1001L).getStatus());
        assertEquals(EventEndCause.MEMBERSHIP_ENDED,tickets.get(1001L).getEndCause());
        assertEquals(EventParticipationStatus.REGISTERED,tickets.get(1000L).getStatus());
    }
    @Test void clubArchiveCancelsDraftAndPublishedEventsAndRegisteredTickets()throws Exception{
        action("/api/clubs/10/archive",2,0).andExpect(status().isOk());
        assertEquals(EventStatus.CANCELLED,calendar.get(30L).getStatus());assertEquals(EventStatus.CANCELLED,calendar.get(32L).getStatus());
        assertEquals(EventStatus.PUBLISHED,calendar.get(40L).getStatus());
        assertTrue(tickets.values().stream().allMatch(p -> p.getStatus()==EventParticipationStatus.CANCELLED));
    }
    @Test void universityChangeIsBlockedForActiveEventEvenWithoutClubMembership()throws Exception{
        ticket(1002,30,8);assertThrows(ResourceConflictException.class,() -> context.getBean(StudentProfilePolicy.class).requireUniversityChangeAllowed(8L));
        clock.value=NOW.plusSeconds(10801);assertDoesNotThrow(() -> context.getBean(StudentProfilePolicy.class).requireUniversityChangeAllowed(8L));
    }
    @Test void revokedRoleOrMembershipWhileWaitingForLockCannotAuthorizeWrite()throws Exception{
        doAnswer(i -> {people.get(3L).revokeRole(Role.CLUB_ADMIN);return null;}).when(em).refresh(people.get(3L));
        action("/api/clubs/10/events/32/publish",3,0).andExpect(status().isForbidden());verify(events,never()).saveAndFlush(any());
    }
    @Test void revokedMembershipUnderSharedClubLockIsRechecked()throws Exception{
        doAnswer(i -> {field(members.get(103L),"status",ClubMembershipStatus.SUSPENDED);return null;}).when(em).refresh(catalog.get(10L),LockModeType.PESSIMISTIC_READ);
        action("/api/clubs/10/events/32/publish",3,0).andExpect(status().isForbidden());
    }
    @Test void writerLocksUsersInOrderThenClubThenEvent()throws Exception{
        clock.value=NOW.plusSeconds(5400);action("/api/clubs/10/events/30/participations/1000/check-in",5,0).andExpect(status().isOk());
        var order=inOrder(users,clubs,events);order.verify(users).findByIdForUpdate(4L);order.verify(users).findByIdForUpdate(5L);
        order.verify(clubs).findByIdForShare(10L);order.verify(events).findForUpdate(30L,10L);
    }
    @Test void directServiceCallsCannotBypassClubOwnership() {
        try{
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("6","unused",List.of(new SimpleGrantedAuthority("ROLE_CLUB_ADMIN"))));
            assertThrows(AccessDeniedException.class,() -> context.getBean(EventService.class).publish(10L,32L,new EventActionRequest(0L)));
            assertThrows(AccessDeniedException.class,() -> context.getBean(EventParticipationService.class).decide(10L,30L,1000L,new EventActionRequest(0L),null,EventParticipationService.Decision.CHECK_IN));
        }finally{SecurityContextHolder.clearContext();}
    }
    @Test void closingBoundaryIsExclusiveAndOpeningBoundaryIsInclusive()throws Exception{
        clock.value=calendar.get(30L).getRegistrationOpensAt();postAs("/api/events/30/participations",8,body("eventVersion",0)).andExpect(status().isCreated());
        clock.value=calendar.get(30L).getRegistrationClosesAt();postAs("/api/events/30/participations",2,body("eventVersion",0)).andExpect(status().isConflict());
    }
    @Test void constraintConflictsDoNotLeakSqlDetails()throws Exception{
        doThrow(new DataIntegrityViolationException("private SQL and table details")).when(participants).saveAndFlush(any());
        postAs("/api/events/30/participations",8,body("eventVersion",0)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("İşlem mevcut kayıtlarla çakışıyor. Gönderdiğiniz bilgileri kontrol ediniz."));
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","size=0","size=101"})
    void invalidPaginationIs400(String value)throws Exception{var pair=value.split("=");mvc.perform(get("/api/events").param(pair[0],pair[1]).header("Authorization",token(4))).andExpect(status().isBadRequest());}
    @ParameterizedTest @ValueSource(strings={"Not/AZone","+03:00",""})
    void invalidZoneIs400(String zone)throws Exception{postAs("/api/clubs/10/events",2,createBody().replace("Europe/Istanbul",zone)).andExpect(status().isBadRequest());}
    @Test void invalidScheduleUnsafeCoverAndAmbiguousCapacityPatchAre400()throws Exception{
        postAs("/api/clubs/10/events",2,createBody().replace(NOW.plusSeconds(10800).toString(),NOW.toString())).andExpect(status().isBadRequest());
        postAs("/api/clubs/10/events",2,createBody().replace("\"capacity\":2","\"capacity\":2,\"coverUrl\":\"javascript:alert(1)\"" )).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/clubs/10/events/30").header("Authorization",token(2)).contentType("application/json").content(body("version",0,"unlimited",true,"capacity",1))).andExpect(status().isBadRequest());
    }
    @Test void eventPublicationAndRescheduleEmitDistinctVersionedNotifications()throws Exception {
        var writer=context.getBean(com.campusplus.backend.service.notification.NotificationWriter.class);clearInvocations(writer);
        action("/api/clubs/10/events/32/publish",2,0).andExpect(status().isOk());
        verify(writer).eventPublished(calendar.get(32L),people.get(2L));
        postAs("/api/clubs/10/events/32/reschedule",2,body("version",1,"registrationOpensAt",NOW.toString(),
            "registrationClosesAt",NOW.plusSeconds(3600).toString(),"startsAt",NOW.plusSeconds(7200).toString(),
            "endsAt",NOW.plusSeconds(10800).toString(),"timeZone","Europe/Istanbul","reason","Schedule changed")).andExpect(status().isOk());
        verify(writer).eventChanged(calendar.get(32L),people.get(2L),NotificationType.EVENT_RESCHEDULED);
        postAs("/api/clubs/10/events/32/cancel",2,body("version",2,"reason","Cancelled")).andExpect(status().isOk());
        verify(writer).eventChanged(calendar.get(32L),people.get(2L),NotificationType.EVENT_CANCELLED);
        verify(writer,never()).participationsEnding(any(),any(),any());
    }
    @Test void registrationNotifiesItsActualStudentAndSelfCancellationDoesNotRepeat()throws Exception {
        var writer=context.getBean(com.campusplus.backend.service.notification.NotificationWriter.class);clearInvocations(writer);
        postAs("/api/events/30/participations",8,body("eventVersion",0,"userId",7)).andExpect(status().isCreated());
        var captor=org.mockito.ArgumentCaptor.forClass(EventParticipation.class);
        verify(writer).participationChanged(captor.capture(),eq(people.get(8L)),eq(NotificationType.PARTICIPATION_REGISTERED));
        assertEquals(8L,captor.getValue().getUserId());
        action("/api/events/30/participation/cancel",8,0).andExpect(status().isOk());verifyNoMoreInteractions(writer);
    }
    @Test void membershipLeaveOnlyNotifiesActuallyEndedMembersOnlyTickets()throws Exception {
        var writer=context.getBean(com.campusplus.backend.service.notification.NotificationWriter.class);clearInvocations(writer);
        postAs("/api/clubs/10/membership/leave",4,body("version",0)).andExpect(status().isOk());
        verify(writer).participationsEnding(calendar.get(31L),4L,people.get(4L));verifyNoMoreInteractions(writer);
    }

}
