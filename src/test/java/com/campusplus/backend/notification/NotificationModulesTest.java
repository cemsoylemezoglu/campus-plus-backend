package com.campusplus.backend.notification;
import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.NotificationController;
import com.campusplus.backend.dto.notification.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.notification.*;
import jakarta.persistence.*;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

/** Real JWT/security/MVC/domain policy; persistence is mocked, not a PostgreSQL locking proof. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationModulesTest {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,CurrentActor.class,ClubPolicy.class,ClubMembershipPolicy.class,ClubWorkspace.class,
        AnnouncementPolicy.class,EventPolicy.class,NotificationReader.class,NotificationService.class,NotificationController.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean ClubRepository clubs(){return mock(ClubRepository.class);}
        @Bean ClubMembershipRepository members(){return mock(ClubMembershipRepository.class);}
        @Bean StudentProfileRepository profiles(){return mock(StudentProfileRepository.class);}
        @Bean UniversityRepository universities(){return mock(UniversityRepository.class);}
        @Bean NotificationRepository notifications(){return mock(NotificationRepository.class);}
        @Bean EntityManager entityManager(){return mock(EntityManager.class);}
        @Bean @Primary Clock clock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean static MethodValidationPostProcessor validation(){var p=new MethodValidationPostProcessor();p.setProxyTargetClass(true);return p;}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;JwtEncoder encoder;
    UserRepository users;ClubMembershipRepository members;StudentProfileRepository profiles;NotificationRepository notifications;EntityManager em;
    final Map<Long,User> people=new HashMap<>();final Map<Long,StudentProfile> academics=new HashMap<>();
    final Map<Long,Notification> inbox=new HashMap<>();final Map<Long,ClubMembership> roster=new HashMap<>();
    final JsonMapper json=JsonMapper.builder().build();Club club,foreign,hidden;Announcement privateAnnouncement,publicAnnouncement;
    Event privateEvent;EventParticipation ownParticipation;University university,other;
    static void field(Object o,String n,Object v){ReflectionTestUtils.setField(o,n,v);}
    @BeforeAll void start(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"JWT_SECRET="+JwtTestKeys.newSecret());context.register(Config.class);context.refresh();
        encoder=context.getBean(JwtEncoder.class);users=context.getBean(UserRepository.class);members=context.getBean(ClubMembershipRepository.class);
        profiles=context.getBean(StudentProfileRepository.class);notifications=context.getBean(NotificationRepository.class);em=context.getBean(EntityManager.class);
        mvc=webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    Club club(long id,ClubStatus status){var c=new Club(university,people.get(4L),"Private Club","club-"+id,"Private description",null,NOW);field(c,"id",id);field(c,"status",status);return c;}
    Announcement announcement(long id,AnnouncementAudience audience,boolean published,Club c){
        var a=new Announcement(c,people.get(4L),"Private Title","Private Body",audience,null,NOW);field(a,"id",id);field(a,"version",0L);if(published)a.publish(NOW);return a;
    }
    Event event(long id,EventAudience audience,boolean published){
        var e=new Event(club,people.get(4L),"Private Event","Description","Hall",null,audience,10,NOW.minusSeconds(60),NOW.plusSeconds(1000),NOW.plusSeconds(2000),NOW.plusSeconds(3000),"Europe/Istanbul",NOW);
        field(e,"id",id);if(published)e.publish(NOW);return e;
    }
    ClubMembership member(long id,long user,boolean owner){
        var m=new ClubMembership(club,academics.get(user),owner,NOW);field(m,"id",id);field(m,"status",ClubMembershipStatus.ACTIVE);field(m,"joinedAt",NOW);roster.put(user,m);return m;
    }
    Notification note(long id,long recipient,NotificationType type,Club c,Object source){
        Notification n=BeanUtils.instantiateClass(Notification.class);field(n,"id",id);field(n,"recipientId",recipient);field(n,"type",type);field(n,"club",c);field(n,"version",0L);field(n,"createdAt",NOW.minusSeconds(60));
        if(source instanceof Announcement a)field(n,"announcement",a);
        if(source instanceof Event e)field(n,"event",e);
        if(source instanceof ClubMembership m)field(n,"membership",m);
        if(source instanceof EventParticipation p){field(n,"participation",p);field(n,"event",p.getEvent());}
        inbox.put(id,n);return n;
    }
    @BeforeEach void fixtures(){
        reset(users,members,profiles,notifications,em);people.clear();academics.clear();inbox.clear();roster.clear();
        university=new University("UNI","University","City","TR",null,NOW);field(university,"id",1L);
        other=new University("OTHER","Other","City","TR",null,NOW);field(other,"id",2L);
        for(long id=2;id<=7;id++){
            var u=new User("Person "+id,"person"+id+"@example.test");field(u,"id",id);u.setPasswordHash("synthetic-test-hash");
            if(id==4)u.grantRole(Role.CLUB_ADMIN);if(id==5)u.grantRole(Role.ADMIN);people.put(id,u);
            if(id!=7)academics.put(id,new StudentProfile(u,id==6?other:university,"Engineering",null,null,NOW));
        }
        club=club(10,ClubStatus.ACTIVE);foreign=club(20,ClubStatus.ACTIVE);hidden=club(30,ClubStatus.PENDING_APPROVAL);
        var own=member(80,2,false);member(81,4,true);
        var another=new ClubMembership(club,academics.get(3L),false,NOW);field(another,"id",82L);
        privateAnnouncement=announcement(50,AnnouncementAudience.CLUB_MEMBERS,true,club);
        publicAnnouncement=announcement(51,AnnouncementAudience.UNIVERSITY_STUDENTS,true,club);
        privateEvent=event(61,EventAudience.CLUB_MEMBERS,true);
        ownParticipation=new EventParticipation(privateEvent,academics.get(2L),NOW);field(ownParticipation,"id",70L);
        var anotherParticipation=new EventParticipation(privateEvent,academics.get(3L),NOW);field(anotherParticipation,"id",71L);
        note(100,2,NotificationType.ANNOUNCEMENT_PUBLISHED,club,privateAnnouncement);
        note(101,2,NotificationType.EVENT_PUBLISHED,club,privateEvent);
        note(102,2,NotificationType.MEMBERSHIP_APPROVED,club,own);
        note(103,2,NotificationType.PARTICIPATION_REGISTERED,club,ownParticipation);
        note(104,3,NotificationType.ANNOUNCEMENT_PUBLISHED,club,publicAnnouncement);
        note(105,5,NotificationType.ANNOUNCEMENT_PUBLISHED,club,publicAnnouncement);
        note(106,2,NotificationType.CLUB_APPROVED,hidden,null);
        note(107,2,NotificationType.MEMBERSHIP_REQUESTED,club,another);
        note(108,2,NotificationType.EVENT_PUBLISHED,club,event(62,EventAudience.UNIVERSITY_STUDENTS,false));
        note(109,2,NotificationType.ANNOUNCEMENT_PUBLISHED,club,announcement(52,AnnouncementAudience.UNIVERSITY_STUDENTS,false,club));
        note(110,2,NotificationType.ANNOUNCEMENT_PUBLISHED,club,publicAnnouncement);
        note(111,2,NotificationType.CLUB_ARCHIVED,club,null);
        note(112,2,NotificationType.PARTICIPATION_REGISTERED,club,anotherParticipation);
        note(113,2,NotificationType.ANNOUNCEMENT_PUBLISHED,club,announcement(53,AnnouncementAudience.UNIVERSITY_STUDENTS,true,foreign));
        when(users.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(profiles.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(academics.get(i.<Long>getArgument(0))));
        when(members.findByClubIdInAndUserId(anyCollection(),anyLong())).thenAnswer(i -> Optional.ofNullable(roster.get(i.<Long>getArgument(1))).stream().filter(m -> i.<Collection<Long>>getArgument(0).contains(m.getClub().getId())).toList());
        when(notifications.inbox(anyLong(),anyBoolean(),any())).thenAnswer(i -> {
            var list=inbox.values().stream().filter(n -> n.getRecipientId().equals(i.getArgument(0))&&(!i.<Boolean>getArgument(1)||n.getReadAt()==null))
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed().thenComparing(Notification::getId,Comparator.reverseOrder())).toList();
            Pageable p=i.getArgument(2);int start=(int)Math.min(p.getOffset(),list.size());return new PageImpl<>(list.subList(start,Math.min(list.size(),start+p.getPageSize())),p,list.size());
        });
        when(notifications.findByIdAndRecipientId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(inbox.get(i.<Long>getArgument(0))).filter(n -> n.getRecipientId().equals(i.getArgument(1))));
        when(notifications.countByRecipientIdAndReadAtIsNull(anyLong())).thenAnswer(i -> inbox.values().stream().filter(n -> n.getRecipientId().equals(i.getArgument(0))&&n.getReadAt()==null).count());
        when(notifications.saveAndFlush(any())).thenAnswer(i -> {Notification n=i.getArgument(0);field(n,"version",n.getVersion()+1);return n;});
        when(notifications.readAll(anyLong(),anyLong(),any())).thenAnswer(i -> {
            int changed=0;for(var n:inbox.values())if(n.getRecipientId().equals(i.getArgument(0))&&n.getId()<=i.<Long>getArgument(1)&&n.getReadAt()==null){n.read(i.getArgument(2));field(n,"version",n.getVersion()+1);changed++;}return changed;
        });
    }
    String token(long id){return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
        .issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE)).subject(Long.toString(id)).issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build())).getTokenValue();}
    ResultActions getAs(long user,String query)throws Exception{return mvc.perform(get("/api/me/notifications"+query).header("Authorization",token(user)));}
    ResultActions action(long user,long id,String verb,long version)throws Exception{return mvc.perform(post("/api/me/notifications/"+id+"/"+verb).header("Authorization",token(user)).contentType("application/json").content("{\"version\":"+version+"}"));}
    ResultActions all(long user,long cutoff)throws Exception{return mvc.perform(post("/api/me/notifications/read-all").header("Authorization",token(user)).contentType("application/json").content("{\"upToId\":"+cutoff+"}"));}
    @Test void anonymousCannotReadOrMutateInbox()throws Exception {
        mvc.perform(get("/api/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/notifications/100/read").contentType("application/json").content("{\"version\":0}")).andExpect(status().isUnauthorized());
    }
    @Test void listAndCountAreAlwaysScopedToTokenUser()throws Exception {
        getAs(3,"?recipientId=2").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(104));
        getAs(5,"").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        verify(notifications).inbox(eq(3L),eq(false),any());verify(notifications).inbox(eq(5L),eq(false),any());
        getAs(3,"/unread-count").andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    }
    @ParameterizedTest @ValueSource(longs={2,5}) void adminAndOtherUsersCannotReadOrUnreadOthersNotification(long user)throws Exception {
        action(user,104,"read",0).andExpect(status().isNotFound());action(user,104,"unread",0).andExpect(status().isNotFound());
        verify(notifications,never()).saveAndFlush(any());
    }
    @Test void ownReadIsVersionedAndRepeatedReadIsIdempotent()throws Exception {
        action(2,100,"read",9).andExpect(status().isConflict());
        action(2,100,"read",0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.readAt").exists()).andExpect(header().string("Cache-Control","no-store"));
        action(2,100,"read",0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));verify(notifications,times(1)).saveAndFlush(any());
    }
    @Test void unreadRequiresCurrentVersion()throws Exception {
        action(2,100,"read",0).andExpect(status().isOk());
        action(2,100,"unread",0).andExpect(status().isConflict());
        action(2,100,"unread",1).andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isEmpty()).andExpect(jsonPath("$.version").value(2));
    }
    @Test void readAllIsBoundedScopedAndIncrementsVersion()throws Exception {
        all(2,103).andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(4));
        assertNull(inbox.get(104L).getReadAt());assertNull(inbox.get(110L).getReadAt());
        assertEquals(1L,inbox.get(100L).getVersion());
        action(2,100,"unread",0).andExpect(status().isConflict());
        verify(notifications).readAll(2L,103L,NOW);
        all(2,103).andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(0));
    }
    @Test void unreadFilterCountAndStablePagingReflectReadState()throws Exception {
        getAs(2,"?size=1").andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(113)).andExpect(jsonPath("$.totalElements").value(12));
        all(2,103).andExpect(status().isOk());
        getAs(2,"?unreadOnly=true").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(8));
        getAs(2,"/unread-count").andExpect(status().isOk()).andExpect(jsonPath("$.count").value(8));
    }
    NotificationResponse resolve(long id,long user){var reader=context.getBean(NotificationReader.class);var n=inbox.get(id);var local=roster.get(user);return reader.response(n,new NotificationReader.Context(people.get(user),academics.get(user),local==null?Map.of():Map.of(local.getClub().getId(),local)));}
    @Test void membershipLossHidesPrivateAnnouncementAndEventTargets() {
        assertTrue(resolve(100,2).available());assertTrue(resolve(101,2).available());
        field(roster.get(2L),"status",ClubMembershipStatus.LEFT);
        for(long id:new long[]{100,101}){var dto=resolve(id,2);assertFalse(dto.available());assertNull(dto.target());assertFalse(dto.message().contains("Private"));}
    }
    @Test void universityChangeOrWithdrawalHidesAnnouncementTarget() {
        assertTrue(resolve(110,2).available());academics.get(2L).update(other,"Engineering",null,null,NOW);
        assertFalse(resolve(110,2).available());
        academics.get(2L).update(university,"Engineering",null,null,NOW);publicAnnouncement.withdraw(people.get(4L),"Private reason",NOW);
        assertFalse(resolve(110,2).available());
    }
    @ParameterizedTest @ValueSource(longs={106,107,108,109,112,113}) void hiddenOrInconsistentSourceProducesOnlyGenericShell(long id) {
        var dto=resolve(id,2);assertFalse(dto.available());assertNull(dto.target());assertFalse(dto.toString().contains("Private"));
    }
    @Test void personalMembershipAndParticipationHistoryRemainAccessibleToOwner() {
        field(roster.get(2L),"status",ClubMembershipStatus.LEFT);field(club,"status",ClubStatus.ARCHIVED);
        assertTrue(resolve(102,2).available());assertTrue(resolve(103,2).available());
        assertEquals(NotificationResource.EVENT_PARTICIPATION,resolve(103,2).target().resource());
    }
    @Test void managementSourceUsesCurrentRolesNotNotificationReceipt() {
        field(inbox.get(107L),"recipientId",4L);
        assertTrue(resolve(107,4).available());people.get(4L).revokeRole(Role.CLUB_ADMIN);
        assertFalse(resolve(107,4).available());
    }
    @Test void sourceMetadataDoesNotLeakPrivateTextOrReasons()throws Exception {
        var result=getAs(2,"").andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String content=result.getResponse().getContentAsString();assertFalse(content.contains("Private"));assertFalse(content.contains("password"));assertFalse(content.contains("sourceKey"));assertFalse(content.contains("actorId"));
    }
    @Test void unreadStateCanChangeEvenWhenSourceIsNoLongerVisible()throws Exception {
        action(2,109,"read",0).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.target").isEmpty());
    }
    @Test void listUsesOneMembershipBatchInsteadOfPerNotificationLookups()throws Exception {
        getAs(2,"").andExpect(status().isOk());
        verify(members,times(1)).findByClubIdInAndUserId(anyCollection(),eq(2L));verify(members,never()).findByClubIdAndUserId(anyLong(),anyLong());verify(profiles,times(1)).findById(2L);
    }
    @ParameterizedTest @ValueSource(strings={"?size=0","?size=101","?page=-1"}) void invalidPagingIs400(String query)throws Exception {getAs(2,query).andExpect(status().isBadRequest());}
    @Test void invalidVersionsAndCutoffsAre400()throws Exception {
        action(2,100,"read",-1).andExpect(status().isBadRequest());all(2,0).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/notifications/100/read").header("Authorization",token(2)).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void emptyInboxDoesNotIssueAnEmptyInMembershipQuery()throws Exception {
        getAs(7,"").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        verify(members,never()).findByClubIdInAndUserId(anyCollection(),anyLong());
    }
    @Test void backwardClockCannotViolateReadTimestampConstraint()throws Exception {
        field(inbox.get(100L),"createdAt",NOW.plusSeconds(60));action(2,100,"read",0).andExpect(status().isOk());
        assertEquals(NOW.plusSeconds(60),inbox.get(100L).getReadAt());
    }
}
