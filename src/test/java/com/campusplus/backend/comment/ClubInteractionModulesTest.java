package com.campusplus.backend.comment;

import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.clubinteraction.*;
import com.campusplus.backend.service.notification.ClubCommentNotificationWriter;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real JWT/MVC/method security/services; persistence mocks are not evidence of SQL or real lock behavior. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubInteractionModulesTest {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,CurrentActor.class,ClubPolicy.class,ClubMembershipPolicy.class,ClubWorkspace.class,
        ClubInteractionPolicy.class,ClubInteractionWorkspace.class,ClubCommentWorkspace.class,ClubCommentService.class,
        ClubLikeService.class,ClubCommentQueryService.class,ClubCommentModerationService.class,EventCommentController.class,
        AnnouncementCommentController.class,EventLikeController.class,AnnouncementLikeController.class,
        OwnClubCommentController.class,ClubCommentModerationController.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean ClubRepository clubs(){return mock(ClubRepository.class);}
        @Bean ClubMembershipRepository memberships(){return mock(ClubMembershipRepository.class);}
        @Bean StudentProfileRepository profiles(){return mock(StudentProfileRepository.class);}
        @Bean UniversityRepository universities(){return mock(UniversityRepository.class);}
        @Bean EventRepository events(){return mock(EventRepository.class);}
        @Bean AnnouncementRepository announcements(){return mock(AnnouncementRepository.class);}
        @Bean ClubCommentRepository comments(){return mock(ClubCommentRepository.class);}
        @Bean ClubContentLikeRepository likes(){return mock(ClubContentLikeRepository.class);}
        @Bean ClubCommentLikeRepository commentLikes(){return mock(ClubCommentLikeRepository.class);}
        @Bean ClubCommentModerationRepository audit(){return mock(ClubCommentModerationRepository.class);}
        @Bean ClubCommentNotificationWriter notices(){return mock(ClubCommentNotificationWriter.class);}
        @Bean EntityManager em(){return mock(EntityManager.class);}
        @Bean @Primary Clock clock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean static MethodValidationPostProcessor validation(){var p=new MethodValidationPostProcessor();p.setProxyTargetClass(true);return p;}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;JwtEncoder encoder;
    UserRepository users;ClubRepository clubs;ClubMembershipRepository memberships;StudentProfileRepository profiles;
    UniversityRepository universities;EventRepository events;AnnouncementRepository announcements;ClubCommentRepository comments;
    ClubContentLikeRepository likes;ClubCommentLikeRepository commentLikes;ClubCommentModerationRepository audit;ClubCommentNotificationWriter notices;
    final Map<Long,User> people=new HashMap<>();final Map<Long,StudentProfile> academics=new HashMap<>();
    final Map<Long,ClubComment> discussion=new HashMap<>();final Map<String,ClubContentLike> sourceLikes=new HashMap<>();
    final Map<String,ClubCommentLike> reactions=new HashMap<>();
    final JsonMapper json=JsonMapper.builder().build();Club club;University university;Event event;Announcement announcement;ClubMembership manager;
    AtomicLong ids=new AtomicLong();
    static void field(Object target,String name,Object value){ReflectionTestUtils.setField(target,name,value);}
    @BeforeAll void start(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"JWT_SECRET="+JwtTestKeys.newSecret());
        context.register(Config.class);context.refresh();encoder=context.getBean(JwtEncoder.class);
        users=context.getBean(UserRepository.class);clubs=context.getBean(ClubRepository.class);memberships=context.getBean(ClubMembershipRepository.class);
        profiles=context.getBean(StudentProfileRepository.class);universities=context.getBean(UniversityRepository.class);events=context.getBean(EventRepository.class);
        announcements=context.getBean(AnnouncementRepository.class);comments=context.getBean(ClubCommentRepository.class);likes=context.getBean(ClubContentLikeRepository.class);
        commentLikes=context.getBean(ClubCommentLikeRepository.class);audit=context.getBean(ClubCommentModerationRepository.class);notices=context.getBean(ClubCommentNotificationWriter.class);
        mvc=webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void setup(){
        reset(users,clubs,memberships,profiles,universities,events,announcements,comments,likes,commentLikes,audit,notices);
        people.clear();academics.clear();discussion.clear();sourceLikes.clear();reactions.clear();ids.set(100);
        university=new University("UNI","University","City","TR",null,NOW);field(university,"id",10L);
        var foreign=new University("OTHER","Other","City","TR",null,NOW);field(foreign,"id",20L);
        for(long id=1;id<=6;id++){
            var u=new User("Student "+id,"student"+id+"@example.test");field(u,"id",id);u.setPasswordHash("synthetic-test-hash");people.put(id,u);
            if(id==3||id==6)u.grantRole(Role.CLUB_ADMIN);if(id==4)u.grantRole(Role.ADMIN);
            academics.put(id,new StudentProfile(u,id==5?foreign:university,"Engineering",null,null,NOW));
        }
        club=new Club(university,people.get(3L),"Club","club","Description",null,NOW);field(club,"id",100L);field(club,"status",ClubStatus.ACTIVE);
        manager=new ClubMembership(club,academics.get(3L),true,NOW);field(manager,"status",ClubMembershipStatus.ACTIVE);
        event=new Event(club,people.get(3L),"Event","Description","Hall",null,EventAudience.UNIVERSITY_STUDENTS,10,
            NOW,NOW.plusSeconds(300),NOW.plusSeconds(600),NOW.plusSeconds(1200),"Europe/Istanbul",NOW);field(event,"id",7L);event.publish(NOW);
        announcement=new Announcement(club,people.get(3L),"Announcement","Body",AnnouncementAudience.UNIVERSITY_STUDENTS,event,NOW);field(announcement,"id",7L);announcement.publish(NOW);
        when(users.findById(anyLong())).thenAnswer(a->Optional.ofNullable(people.get(a.getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(a->Optional.ofNullable(people.get(a.getArgument(0))));
        when(profiles.findById(anyLong())).thenAnswer(a->Optional.ofNullable(academics.get(a.getArgument(0))));
        when(clubs.findByIdForShare(100L)).thenReturn(Optional.of(club));when(universities.findByIdForShare(10L)).thenReturn(Optional.of(university));
        when(memberships.findByClubIdAndUserId(100L,3L)).thenReturn(Optional.of(manager));
        when(events.findById(7L)).thenReturn(Optional.of(event));when(events.findForUpdate(7L,100L)).thenReturn(Optional.of(event));
        when(announcements.findById(7L)).thenReturn(Optional.of(announcement));when(announcements.findForUpdate(7L,100L)).thenReturn(Optional.of(announcement));
        when(comments.findById(anyLong())).thenAnswer(a->Optional.ofNullable(discussion.get(a.getArgument(0))));
        when(comments.lock(anyLong(),any(),any())).thenAnswer(a->Optional.ofNullable(discussion.get(a.getArgument(0))).filter(c->Objects.equals(c.source().eventId(),a.getArgument(1))&&Objects.equals(c.source().announcementId(),a.getArgument(2))));
        when(comments.saveAndFlush(any())).thenAnswer(a->{ClubComment c=a.getArgument(0);if(c.getId()==null){field(c,"id",ids.incrementAndGet());field(c,"version",0L);}else field(c,"version",c.getVersion()+1);discussion.put(c.getId(),c);return c;});
        when(likes.own(any(),any(),anyLong())).thenAnswer(a->Optional.ofNullable(sourceLikes.get(a.getArgument(0)+":"+a.getArgument(1)+":"+a.getArgument(2))));
        when(likes.saveAndFlush(any())).thenAnswer(a->{ClubContentLike l=a.getArgument(0);field(l,"id",ids.incrementAndGet());sourceLikes.put((l.getEvent()==null?null:l.getEvent().getId())+":"+(l.getAnnouncement()==null?null:l.getAnnouncement().getId())+":"+l.getUser().getId(),l);return l;});
        when(commentLikes.findByCommentIdAndUserId(anyLong(),anyLong())).thenAnswer(a->Optional.ofNullable(reactions.get(a.getArgument(0)+":"+a.getArgument(1))));
        when(commentLikes.saveAndFlush(any())).thenAnswer(a->{ClubCommentLike l=a.getArgument(0);field(l,"id",ids.incrementAndGet());reactions.put(l.getComment().getId()+":"+l.getUser().getId(),l);return l;});
        when(comments.own(anyLong(),any())).thenReturn(Page.empty());when(comments.managed(any(),any())).thenReturn(Page.empty());
        when(audit.history(anyLong(),any(),any())).thenReturn(Page.empty());
    }
    String path(ClubSourceKind kind){return kind==ClubSourceKind.EVENT?"/api/events/7":"/api/announcements/7";}
    ResultActions request(String method,String path,Long user,Object body)throws Exception{
        var r=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),path).contentType("application/json");
        if(user!=null){Instant now=NOW;var claims=JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE)).subject(user.toString()).issuedAt(now).expiresAt(now.plusSeconds(120)).build();
            r.header("Authorization","Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue());}
        if(body!=null)r.content(json.writeValueAsString(body));return mvc.perform(r);
    }
    JsonNode result(int code,String method,String path,Long user,Object body)throws Exception{
        var response=request(method,path,user,body).andExpect(status().is(code)).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("passwordHash"));
        return response.getContentAsString().isEmpty()?json.createObjectNode():json.readTree(response.getContentAsString());
    }
    long create(ClubSourceKind kind,long actor)throws Exception{return result(201,"POST",path(kind)+"/comments",actor,Map.of("body","Comment body")).path("id").asLong();}
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void createsRepliesEditsAndTerminalOwnRemoval(ClubSourceKind kind)throws Exception{
        long root=create(kind,1);var reply=result(201,"POST",path(kind)+"/comments/"+root+"/replies",2L,Map.of("body","Reply body"));
        assertEquals(kind.name(),reply.path("source").path("kind").asText());assertEquals(root,reply.path("rootId").asLong());
        result(200,"PATCH",path(kind)+"/comments/"+root,1L,Map.of("version",0,"body","Updated body"));
        result(409,"PATCH",path(kind)+"/comments/"+root,1L,Map.of("version",0,"body","Stale body"));
        result(200,"POST","/api/me/club-comments/"+root+"/remove",1L,Map.of("version",1));
        result(404,"POST",path(kind)+"/comments/"+root+"/replies",2L,Map.of("body","Late reply"));
        result(404,"PATCH",path(kind)+"/comments/"+root,1L,Map.of("version",2,"body","Resurrect"));
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void rejectsOtherOwnerAndNestedOrCrossSourceReplies(ClubSourceKind kind)throws Exception{
        long root=create(kind,1);long reply=result(201,"POST",path(kind)+"/comments/"+root+"/replies",2L,Map.of("body","Reply")).path("id").asLong();
        result(404,"PATCH",path(kind)+"/comments/"+root,2L,Map.of("version",0,"body","Hijacked"));
        result(404,"POST","/api/me/club-comments/"+root+"/remove",2L,Map.of("version",0));
        result(409,"POST",path(kind)+"/comments/"+reply+"/replies",1L,Map.of("body","Nested"));
        var other=kind==ClubSourceKind.EVENT?ClubSourceKind.ANNOUNCEMENT:ClubSourceKind.EVENT;
        result(404,"POST",path(other)+"/comments/"+root+"/replies",1L,Map.of("body","Wrong source"));
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void likeIsIdempotentAndRelikeDoesNotNotifyAgain(ClubSourceKind kind)throws Exception{
        long id=create(kind,1);String base=path(kind);
        for(int i=0;i<2;i++)result(200,"PUT",base+"/like",2L,null);
        result(204,"DELETE",base+"/like",2L,null);result(200,"PUT",base+"/like",2L,null);
        for(int i=0;i<2;i++)result(200,"PUT",base+"/comments/"+id+"/like",2L,null);
        result(204,"DELETE",base+"/comments/"+id+"/like",2L,null);result(200,"PUT",base+"/comments/"+id+"/like",2L,null);
        verify(notices,times(1)).sourceLiked(any(),any());verify(notices,times(1)).commentLiked(any(),any());
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void cleanupOwnContentStillWorksAfterSourceAccessLoss(ClubSourceKind kind)throws Exception{
        long id=create(kind,1);result(200,"PUT",path(kind)+"/like",1L,null);result(200,"PUT",path(kind)+"/comments/"+id+"/like",1L,null);
        field(event,"archivedAt",NOW);field(announcement,"archivedAt",NOW);
        result(404,"POST",path(kind)+"/comments",1L,Map.of("body","Closed"));
        result(200,"GET","/api/me/club-comments/"+id,1L,null);
        result(204,"DELETE",path(kind)+"/like",1L,null);result(204,"DELETE",path(kind)+"/comments/"+id+"/like",1L,null);
        result(200,"POST","/api/me/club-comments/"+id+"/remove",1L,Map.of("version",0));
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void moderationScopesStayIndependentAndLocalResponseHasNoText(ClubSourceKind kind)throws Exception{
        long id=create(kind,1);String local=path(kind)+"/comments/"+id;
        result(403,"POST",local+"/hide",4L,Map.of("version",0,"reason","Admin is not local manager"));
        result(403,"POST",local+"/hide",6L,Map.of("version",0,"reason","Marker is not membership"));
        result(200,"POST","/api/admin/club-comments/"+id+"/hide",4L,Map.of("version",0,"reason","Platform reason"));
        var response=result(200,"POST",local+"/hide",3L,Map.of("version",1,"reason","Local reason"));
        assertFalse(response.has("body"));assertFalse(response.has("author"));assertFalse(response.has("reason"));
        result(200,"POST",local+"/restore",3L,Map.of("version",2,"reason","Local restore"));assertTrue(discussion.get(id).isPlatformHidden());
        result(404,"PUT",local+"/like",2L,null);
        result(403,"POST","/api/admin/club-comments/"+id+"/restore",3L,Map.of("version",3,"reason","Not admin"));
        result(200,"GET",local+"/moderation-actions",3L,null);
        verify(audit).history(eq(id),eq(CommentModerationScope.RESOURCE),any());
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void authValidationAndUniversityBoundaries(ClubSourceKind kind)throws Exception{
        result(401,"POST",path(kind)+"/comments",null,Map.of("body","Comment"));
        result(400,"POST",path(kind)+"/comments",1L,Map.of("body","  "));
        result(400,"POST",path(kind)+"/comments",1L,Map.of("body","x".repeat(3001)));
        result(kind==ClubSourceKind.EVENT?403:404,"POST",path(kind)+"/comments",5L,Map.of("body","Foreign"));
        result(403,"GET","/api/admin/club-comments",1L,null);
        result(200,"GET","/api/admin/club-comments",4L,null);
        result(400,"GET",path(kind)+"/comments?size=101",1L,null);
    }
    @Test void cancelledEventReadAndLocalModerationRemainButNewWritesClose()throws Exception{
        long id=create(ClubSourceKind.EVENT,1);field(event,"status",EventStatus.CANCELLED);
        result(403,"POST",path(ClubSourceKind.EVENT)+"/comments",1L,Map.of("body","Closed"));
        result(403,"PUT",path(ClubSourceKind.EVENT)+"/like",1L,null);
        result(200,"POST",path(ClubSourceKind.EVENT)+"/comments/"+id+"/hide",3L,Map.of("version",0,"reason","Moderate cancelled thread"));
    }
    @Test void unlikeWrongSourceOrUnknownLikeIsNoop()throws Exception{
        long id=create(ClubSourceKind.EVENT,1);result(200,"PUT","/api/events/7/comments/"+id+"/like",2L,null);
        result(204,"DELETE","/api/announcements/7/comments/"+id+"/like",2L,null);
        assertTrue(reactions.get(id+":2").isActive());result(204,"DELETE","/api/announcements/999/like",2L,null);
    }
}
