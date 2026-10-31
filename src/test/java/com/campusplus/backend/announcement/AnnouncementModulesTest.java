package com.campusplus.backend.announcement;

import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.announcement.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.announcement.*;
import com.campusplus.backend.service.club.*;
import com.campusplus.backend.service.event.EventLifecycleService;
import jakarta.persistence.*;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real JWT, security, MVC and domain services; mocked persistence, not a database race test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnnouncementModulesTest {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,CurrentActor.class,ClubPolicy.class,ClubMembershipPolicy.class,ClubWorkspace.class,
        ClubSettings.class,ClubService.class,ClubQueryService.class,ClubController.class,StudentProfilePolicy.class,
        EventPolicy.class,AnnouncementPolicy.class,AnnouncementWorkspace.class,AnnouncementService.class,
        AnnouncementQueryService.class,AnnouncementLifecycleService.class,AnnouncementController.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean com.campusplus.backend.service.notification.NotificationWriter notificationWriter(){return mock(com.campusplus.backend.service.notification.NotificationWriter.class);}
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean ClubRepository clubs(){return mock(ClubRepository.class);}
        @Bean ClubMembershipRepository members(){return mock(ClubMembershipRepository.class);}
        @Bean StudentProfileRepository profiles(){return mock(StudentProfileRepository.class);}
        @Bean UniversityRepository universities(){return mock(UniversityRepository.class);}
        @Bean EventRepository events(){return mock(EventRepository.class);}
        @Bean EventParticipationRepository participants(){return mock(EventParticipationRepository.class);}
        @Bean AnnouncementRepository announcements(){return mock(AnnouncementRepository.class);}
        @Bean EventLifecycleService eventLifecycle(){return mock(EventLifecycleService.class);}
        @Bean EntityManager entityManager(){return mock(EntityManager.class);}
        @Bean @Primary Clock clock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean static MethodValidationPostProcessor validation(){var p=new MethodValidationPostProcessor();p.setProxyTargetClass(true);return p;}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;JwtEncoder encoder;
    UserRepository users;ClubRepository clubs;ClubMembershipRepository members;StudentProfileRepository profiles;
    UniversityRepository universities;EventRepository events;AnnouncementRepository announcements;EntityManager em;
    final Map<Long,User> people=new HashMap<>();final Map<Long,StudentProfile> academics=new HashMap<>();
    final Map<Long,Club> catalog=new HashMap<>();final Map<Long,ClubMembership> roster=new HashMap<>();
    final Map<Long,Event> calendar=new HashMap<>();final Map<Long,Announcement> publications=new HashMap<>();
    final JsonMapper json=JsonMapper.builder().build();University university,other;
    static void field(Object bean,String name,Object value){ReflectionTestUtils.setField(bean,name,value);}
    @BeforeAll void start(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"JWT_SECRET="+JwtTestKeys.newSecret());
        context.register(Config.class);context.refresh();encoder=context.getBean(JwtEncoder.class);
        users=context.getBean(UserRepository.class);clubs=context.getBean(ClubRepository.class);members=context.getBean(ClubMembershipRepository.class);
        profiles=context.getBean(StudentProfileRepository.class);universities=context.getBean(UniversityRepository.class);
        events=context.getBean(EventRepository.class);announcements=context.getBean(AnnouncementRepository.class);em=context.getBean(EntityManager.class);
        mvc=webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
    ClubMembership local(long club,long user){return roster.values().stream().filter(m -> m.getClub().getId()==club&&m.getUserId()==user).findFirst().orElse(null);}
    Announcement publication(long id,long club,AnnouncementAudience audience,boolean published){
        var a=new Announcement(catalog.get(club),people.get(club==10?2L:6L),"Announcement "+id,"Safe body",audience,null,NOW.minusSeconds(120));
        field(a,"id",id);field(a,"version",0L);if(published)a.publish(NOW.minusSeconds(60));publications.put(id,a);return a;
    }
    @BeforeEach void fixtures(){
        reset(users,clubs,members,profiles,universities,events,announcements,em,context.getBean(EventLifecycleService.class));
        people.clear();academics.clear();catalog.clear();roster.clear();calendar.clear();publications.clear();
        university=new University("UNI","University","City","TR",null,NOW);field(university,"id",1L);
        other=new University("OTHER","Other","City","TR",null,NOW);field(other,"id",2L);
        for(long id=2;id<=9;id++){
            var u=new User("Person "+id,"person"+id+"@example.test");field(u,"id",id);u.setPasswordHash("synthetic-test-hash");
            if(id==2||id==3||id==6||id==9)u.grantRole(Role.CLUB_ADMIN);if(id==5)u.grantRole(Role.ADMIN);people.put(id,u);
            academics.put(id,new StudentProfile(u,id==7?other:university,"Engineering",null,null,NOW));
        }
        for(long id:new long[]{10,20}){
            Club c=new Club(university,people.get(id==10?2L:6L),"Club "+id,"club-"+id,"Description",null,NOW);
            field(c,"id",id);field(c,"version",0L);field(c,"status",ClubStatus.ACTIVE);catalog.put(id,c);
        }
        for(long id:new long[]{2,3,4,6}){
            var m=new ClubMembership(catalog.get(id==6?20L:10L),academics.get(id),id==2||id==6,NOW);
            field(m,"id",id+100);field(m,"version",0L);field(m,"status",ClubMembershipStatus.ACTIVE);field(m,"joinedAt",NOW);
            if(id==3)field(m,"role",ClubMembershipRole.MANAGER);roster.put(m.getId(),m);
        }
        for(long id:new long[]{30,31,32,40}){
            Club c=catalog.get(id==40?20L:10L);var e=new Event(c,people.get(2L),"Event "+id,"Description","Hall",null,
                id==31?EventAudience.CLUB_MEMBERS:EventAudience.UNIVERSITY_STUDENTS,10,NOW.minusSeconds(60),NOW.plusSeconds(3600),NOW.plusSeconds(3600),NOW.plusSeconds(7200),"Europe/Istanbul",NOW);
            field(e,"id",id);field(e,"version",0L);if(id!=32)e.publish(NOW);calendar.put(id,e);
        }
        publication(50,10,AnnouncementAudience.UNIVERSITY_STUDENTS,false);
        publication(51,10,AnnouncementAudience.UNIVERSITY_STUDENTS,true);
        publication(52,10,AnnouncementAudience.CLUB_MEMBERS,true);
        publication(60,20,AnnouncementAudience.UNIVERSITY_STUDENTS,false);
        when(users.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profiles.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(academics.get(i.<Long>getArgument(0))));
        when(universities.findByIdForShare(anyLong())).thenAnswer(i -> Optional.of(i.<Long>getArgument(0)==1?university:other));
        when(clubs.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.findByIdForShare(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(members.findByClubIdAndUserId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(local(i.getArgument(0),i.getArgument(1))));
        when(members.activeManagerUserIds(anyLong())).thenAnswer(i -> roster.values().stream().filter(m -> m.getClub().getId().equals(i.getArgument(0))&&m.getStatus()==ClubMembershipStatus.ACTIVE&&m.getRole()!=ClubMembershipRole.MEMBER).map(ClubMembership::getUserId).sorted().toList());
        when(members.hasManagement(anyLong())).thenReturn(false);
        when(events.findForUpdate(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(calendar.get(i.<Long>getArgument(0))).filter(e -> e.getClub().getId().equals(i.getArgument(1))));
        when(announcements.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(publications.get(i.<Long>getArgument(0))));
        when(announcements.findByIdAndClubId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(publications.get(i.<Long>getArgument(0))).filter(a -> a.getClub().getId().equals(i.getArgument(1))));
        when(announcements.findForUpdate(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(publications.get(i.<Long>getArgument(0))).filter(a -> a.getClub().getId().equals(i.getArgument(1))));
        var next=new AtomicLong(70);
        when(announcements.saveAndFlush(any())).thenAnswer(i -> {Announcement a=i.getArgument(0);if(a.getId()==null){field(a,"id",next.getAndIncrement());field(a,"version",0L);}else field(a,"version",a.getVersion()+1);publications.put(a.getId(),a);return a;});
        when(announcements.lockUnarchived(anyLong())).thenAnswer(i -> publications.values().stream().filter(a -> a.getClub().getId().equals(i.getArgument(0))&&a.getArchivedAt()==null).sorted(Comparator.comparing(Announcement::getId)).toList());
        when(announcements.discover(nullable(Long.class),nullable(Long.class),anyString(),anyBoolean(),anyLong(),any())).thenAnswer(i -> {
            long actor=i.getArgument(4);boolean admin=i.getArgument(3);var profile=academics.get(actor);
            var list=publications.values().stream().filter(a -> a.getStatus()==AnnouncementStatus.PUBLISHED&&a.getArchivedAt()==null&&a.getClub().getStatus()==ClubStatus.ACTIVE&&a.getClub().getUniversity().isActive()
                &&(i.getArgument(0)==null||a.getClub().getId().equals(i.getArgument(0)))&&(i.getArgument(1)==null||a.getClub().getUniversity().getId().equals(i.getArgument(1)))
                &&(admin||profile!=null&&profile.getUniversity().getId().equals(a.getClub().getUniversity().getId())&&(a.getAudience()==AnnouncementAudience.UNIVERSITY_STUDENTS||local(a.getClub().getId(),actor)!=null&&local(a.getClub().getId(),actor).getStatus()==ClubMembershipStatus.ACTIVE)))
                .sorted(Comparator.comparing(Announcement::getPublishedAt).reversed().thenComparing(Announcement::getId,Comparator.reverseOrder())).toList();
            Pageable p=i.getArgument(5);int start=(int)Math.min(p.getOffset(),list.size());return new PageImpl<>(list.subList(start,Math.min(list.size(),start+p.getPageSize())),p,list.size());
        });
        when(announcements.managed(anyLong(),nullable(AnnouncementStatus.class),nullable(Boolean.class),any())).thenAnswer(i -> new PageImpl<>(publications.values().stream().filter(a -> a.getClub().getId().equals(i.getArgument(0))).toList()));
    }
    String token(long id){return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
        .issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE)).subject(Long.toString(id)).issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build())).getTokenValue();}
    String body(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return json.writeValueAsString(m);}
    ResultActions getAs(String path,long actor)throws Exception{return mvc.perform(get(path).header("Authorization",token(actor)));}
    ResultActions postAs(String path,long actor,String body)throws Exception{return mvc.perform(post(path).header("Authorization",token(actor)).contentType("application/json").content(body));}
    ResultActions patchAs(String path,long actor,String body)throws Exception{return mvc.perform(patch(path).header("Authorization",token(actor)).contentType("application/json").content(body));}
    String path(long id,String action){return "/api/clubs/10/announcements/"+id+"/"+action;}

    @Test void anonymousRequiresAuthentication()throws Exception {
        mvc.perform(get("/api/announcements")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/clubs/10/announcements").contentType("application/json").content(body("title","Hi","body","Body"))).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(longs={4,6,9}) void membershipOrMarkerAloneCannotManage(long actor)throws Exception {
        postAs("/api/clubs/10/announcements",actor,body("title","Hi","body","Body")).andExpect(status().isForbidden());
        verify(announcements,never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(longs={2,3,5}) void localManagersAndAdminCreateServerOwnedDraft(long actor)throws Exception {
        postAs("/api/clubs/10/announcements",actor,body("title","  Hello  ","body"," Body ","status","PUBLISHED","clubId",20,"createdBy",6))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/announcements/70"))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.announcement.club.id").value(10)).andExpect(jsonPath("$.announcement.title").value("Hello"))
            .andExpect(jsonPath("$.createdBy").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @ParameterizedTest @ValueSource(longs={4,7,8}) void draftIsHiddenFromOrdinaryReaders(long actor)throws Exception {
        getAs("/api/announcements/50",actor).andExpect(status().isNotFound());
    }
    @Test void universityAndMembershipVisibilityUseCurrentState()throws Exception {
        getAs("/api/announcements/51",8).andExpect(status().isOk());
        getAs("/api/announcements/51",7).andExpect(status().isNotFound());
        getAs("/api/announcements/52",8).andExpect(status().isNotFound());
        getAs("/api/announcements/52",4).andExpect(status().isOk());
        field(roster.get(104L),"status",ClubMembershipStatus.SUSPENDED);
        getAs("/api/announcements/52",4).andExpect(status().isNotFound());
        academics.get(8L).update(other,"Engineering",null,null,NOW);
        getAs("/api/announcements/51",8).andExpect(status().isNotFound());
    }
    @Test void missingProfileAndInactiveUniversityCannotReadPublished()throws Exception {
        academics.remove(8L);getAs("/api/announcements/51",8).andExpect(status().isNotFound());
        field(university,"active",false);getAs("/api/announcements/51",4).andExpect(status().isNotFound());
        getAs("/api/announcements/50",5).andExpect(status().isOk());
    }
    @Test void discoveryFiltersPrivateDataBeforePageAndUsesActorIdentity()throws Exception {
        getAs("/api/announcements?size=1",8).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        verify(announcements).discover(isNull(),isNull(),eq("%%"),eq(false),eq(8L),any());
        getAs("/api/announcements?size=1",7).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void parentMismatchIs404AndManagementIsScoped()throws Exception {
        postAs(path(60,"publish"),2,body("version",0)).andExpect(status().isNotFound());
        getAs("/api/clubs/10/announcements/manage",6).andExpect(status().isForbidden());
        getAs("/api/clubs/10/announcements/60/manage",2).andExpect(status().isNotFound());
    }
    @Test void creatorLosesManagementWhenMembershipOrMarkerIsLost()throws Exception {
        field(roster.get(102L),"role",ClubMembershipRole.MEMBER);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isForbidden());
        people.get(3L).revokeRole(Role.CLUB_ADMIN);
        postAs(path(50,"publish"),3,body("version",0)).andExpect(status().isForbidden());
    }
    @Test void permissionIsRecheckedAfterActorLockRefresh()throws Exception {
        doAnswer(i -> {people.get(2L).revokeRole(Role.CLUB_ADMIN);return null;}).when(em).refresh(people.get(2L));
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isForbidden());
        verify(announcements,never()).saveAndFlush(any());
    }
    @Test void publishIsVersionedAndOneWay()throws Exception {
        postAs(path(50,"publish"),2,body("version",99)).andExpect(status().isConflict());
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        postAs(path(50,"publish"),2,body("version",1)).andExpect(status().isConflict());
    }
    @Test void publishedTextCanChangeButAudienceAndLinkCannot()throws Exception {
        patchAs("/api/clubs/10/announcements/51",3,body("version",0,"body","New body")).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("New body"));
        patchAs("/api/clubs/10/announcements/51",3,body("version",1,"audience","CLUB_MEMBERS")).andExpect(status().isConflict());
        patchAs("/api/clubs/10/announcements/51",3,body("version",1,"clearEvent",true)).andExpect(status().isConflict());
    }
    @Test void draftCanLinkAndClearItsOwnPublishedEvent()throws Exception {
        patchAs("/api/clubs/10/announcements/50",2,body("version",0,"eventId",30)).andExpect(status().isOk()).andExpect(jsonPath("$.event.id").value(30));
        patchAs("/api/clubs/10/announcements/50",2,body("version",1,"clearEvent",true)).andExpect(status().isOk()).andExpect(jsonPath("$.event").isEmpty());
    }
    @Test void crossClubAndPrivateAudienceLinksAreRejected()throws Exception {
        postAs("/api/clubs/10/announcements",2,body("title","Hi","body","Body","eventId",40)).andExpect(status().isConflict());
        postAs("/api/clubs/10/announcements",2,body("title","Hi","body","Body","eventId",31)).andExpect(status().isConflict());
        postAs("/api/clubs/10/announcements",2,body("title","Hi","body","Body","eventId",31,"audience","CLUB_MEMBERS")).andExpect(status().isCreated());
    }
    @Test void draftOrArchivedEventPreventsAnnouncementPublication()throws Exception {
        field(publications.get(50L),"event",calendar.get(32L));
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isConflict());
        calendar.get(32L).publish(NOW);calendar.get(32L).archive(NOW);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isConflict());
    }
    @Test void eventAudienceIsRecheckedUnderItsLock()throws Exception {
        field(publications.get(50L),"event",calendar.get(30L));
        doAnswer(i -> {field(calendar.get(30L),"audience",EventAudience.CLUB_MEMBERS);return null;}).when(em).refresh(calendar.get(30L),LockModeType.PESSIMISTIC_WRITE);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isConflict());
    }
    @Test void changedLinkAfterPreviewFailsRatherThanLockingOutOfOrder()throws Exception {
        doAnswer(i -> {field(publications.get(50L),"event",calendar.get(30L));return null;}).when(em).refresh(publications.get(50L),LockModeType.PESSIMISTIC_WRITE);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isConflict());
    }
    @Test void linkedPrivateEventIsRedactedEvenIfLegacyAnnouncementIsUniversityVisible()throws Exception {
        field(publications.get(51L),"event",calendar.get(31L));
        getAs("/api/announcements/51",8).andExpect(status().isOk()).andExpect(jsonPath("$.event").isEmpty());
    }
    @Test void withdrawHidesContentButManagementRetainsReason()throws Exception {
        postAs(path(51,"withdraw"),3,body("version",0,"reason","  Correction required  ")).andExpect(status().isOk()).andExpect(jsonPath("$.withdrawalReason").value("Correction required"));
        getAs("/api/announcements/51",4).andExpect(status().isNotFound());
        getAs("/api/announcements/51",3).andExpect(status().isOk()).andExpect(jsonPath("$.withdrawalReason").doesNotExist());
        getAs("/api/clubs/10/announcements/51/manage",3).andExpect(status().isOk()).andExpect(jsonPath("$.withdrawalReason").value("Correction required"));
        patchAs("/api/clubs/10/announcements/51",3,body("version",1,"title","Changed")).andExpect(status().isConflict());
        postAs(path(51,"publish"),3,body("version",1)).andExpect(status().isConflict());
    }
    @Test void archiveRequiresTerminalStateAndCannotBeRepeated()throws Exception {
        postAs(path(51,"archive"),2,body("version",0)).andExpect(status().isConflict());
        postAs(path(50,"archive"),2,body("version",0)).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").exists());
        postAs(path(50,"archive"),2,body("version",1)).andExpect(status().isConflict());
        postAs(path(50,"publish"),2,body("version",1)).andExpect(status().isConflict());
    }
    @Test void clubArchiveClosesAllUnarchivedAnnouncementsAfterEventSideEffects()throws Exception {
        postAs("/api/clubs/10/archive",2,body("version",0)).andExpect(status().isOk());
        assertEquals(AnnouncementStatus.WITHDRAWN,publications.get(51L).getStatus());
        assertNotNull(publications.get(50L).getArchivedAt());assertNotNull(publications.get(52L).getArchivedAt());
        assertNull(publications.get(60L).getArchivedAt());
        var order=inOrder(context.getBean(EventLifecycleService.class),announcements);
        order.verify(context.getBean(EventLifecycleService.class)).clubArchived(catalog.get(10L),people.get(2L),NOW);
        order.verify(announcements).lockUnarchived(10L);
    }
    @Test void inactiveClubRejectsLocalWritesAndInactiveUniversityRejectsPublishing()throws Exception {
        field(catalog.get(10L),"status",ClubStatus.SUSPENDED);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isConflict());
        postAs(path(51,"withdraw"),2,body("version",0,"reason","Reason")).andExpect(status().isForbidden());
        postAs(path(51,"withdraw"),5,body("version",0,"reason","Reason")).andExpect(status().isOk());
        field(catalog.get(10L),"status",ClubStatus.ACTIVE);field(university,"active",false);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={
        "{\"title\":\" \",\"body\":\"Valid\"}",
        "{\"title\":\"Valid\",\"body\":\" \"}",
        "{\"title\":\"Valid\",\"body\":\"Body\",\"eventId\":-1}",
        "{\"title\":\"Valid\",\"body\":\"Body\",\"audience\":\"EVERYONE\"}"})
    void invalidCreateIs400(String input)throws Exception {postAs("/api/clubs/10/announcements",2,input).andExpect(status().isBadRequest());}
    @Test void invalidPatchAndWithdrawalAre400()throws Exception {
        for(String input:List.of(body("version",0),body("version",0,"title"," "),body("version",-1,"body","x"),body("version",0,"eventId",30,"clearEvent",true)))
            patchAs("/api/clubs/10/announcements/50",2,input).andExpect(status().isBadRequest());
        postAs(path(51,"withdraw"),2,body("version",0,"reason"," ")).andExpect(status().isBadRequest());
    }
    @Test void pageAndFilterBoundsAreValidated()throws Exception {
        getAs("/api/announcements?size=101",2).andExpect(status().isBadRequest());
        getAs("/api/announcements?page=-1",2).andExpect(status().isBadRequest());
        getAs("/api/announcements?clubId=0",2).andExpect(status().isBadRequest());
        getAs("/api/clubs/10/announcements/manage?status=INVALID",2).andExpect(status().isBadRequest());
    }
    @Test void scopedManagementPageReceivesServerSideFilters()throws Exception {
        getAs("/api/clubs/10/announcements/manage?status=WITHDRAWN&archived=true",3).andExpect(status().isOk());
        verify(announcements).managed(eq(10L),eq(AnnouncementStatus.WITHDRAWN),eq(true),any());
    }
    @Test void serviceEntryPointEnforcesSecurityWithoutController() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("4",null,List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        assertThrows(AccessDeniedException.class,() -> context.getBean(AnnouncementService.class).create(10L,new CreateAnnouncementRequest("Title","Body",null,null)));
    }
    @Test void onlyFirstPublicationEmitsAnnouncementNotification()throws Exception {
        var writer=context.getBean(com.campusplus.backend.service.notification.NotificationWriter.class);clearInvocations(writer);
        postAs(path(50,"publish"),2,body("version",0)).andExpect(status().isOk());
        verify(writer).announcementPublished(publications.get(50L),people.get(2L));
        assertEquals(1L,publications.get(50L).getVersion());
        patchAs("/api/clubs/10/announcements/50",2,body("version",1,"body","Correction")).andExpect(status().isOk());
        verifyNoMoreInteractions(writer);
    }

}
