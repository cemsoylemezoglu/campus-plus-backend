package com.campusplus.backend.comment;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.notification.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClubInteractionNotificationTest {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    User actor;University university;Club club;StudentProfile profile;Event event;Announcement announcement;
    ClubMembership membership;ClubWorkspace core;NotificationReader reader;
    static void field(Object target,String name,Object value){ReflectionTestUtils.setField(target,name,value);}
    @BeforeEach void setup(){
        actor=new User("Student","student@example.test");field(actor,"id",1L);
        university=new University("UNI","University","City","TR",null,NOW);field(university,"id",10L);
        profile=new StudentProfile(actor,university,"Engineering",null,null,NOW);
        club=new Club(university,actor,"Club","club","Description",null,NOW);field(club,"id",100L);field(club,"status",ClubStatus.ACTIVE);
        membership=new ClubMembership(club,profile,false,NOW);field(membership,"status",ClubMembershipStatus.ACTIVE);
        event=new Event(club,actor,"Private event","Private body","Hall",null,EventAudience.UNIVERSITY_STUDENTS,10,
            NOW,NOW.plusSeconds(300),NOW.plusSeconds(600),NOW.plusSeconds(1200),"Europe/Istanbul",NOW);field(event,"id",7L);event.publish(NOW);
        announcement=new Announcement(club,actor,"Private announcement","Private body",AnnouncementAudience.UNIVERSITY_STUDENTS,event,NOW);field(announcement,"id",7L);announcement.publish(NOW);
        var policy=new ClubPolicy();core=new ClubWorkspace(mock(ClubRepository.class),mock(ClubMembershipRepository.class),mock(StudentProfileRepository.class),
            mock(UniversityRepository.class),mock(UserRepository.class),mock(CurrentActor.class),policy,new ClubMembershipPolicy(),Clock.fixed(NOW,ZoneOffset.UTC),mock(EntityManager.class));
        reader=new NotificationReader(core,new AnnouncementPolicy(policy),new EventPolicy(policy),new PostPolicy(policy));
    }
    ClubComment comment(ClubSourceKind kind,ClubComment root){
        var c=new ClubComment(kind==ClubSourceKind.EVENT?event:null,kind==ClubSourceKind.ANNOUNCEMENT?announcement:null,actor,root,"Comment text",NOW);
        field(c,"id",root==null?11L:12L);return c;
    }
    Notification notification(NotificationType type,ClubComment comment)throws Exception{
        var ctor=Notification.class.getDeclaredConstructor();ctor.setAccessible(true);var n=ctor.newInstance();
        field(n,"id",99L);field(n,"recipientId",1L);field(n,"type",type);field(n,"club",club);field(n,"clubComment",comment);
        boolean e=type.name().startsWith("EVENT");field(n,e?"event":"announcement",e?event:announcement);
        return n;
    }
    NotificationReader.Context context(){return new NotificationReader.Context(actor,profile,Map.of(100L,membership));}
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void normalTargetIsBoundToSourceAndHiddenRootRemovesIt(ClubSourceKind kind)throws Exception{
        var root=comment(kind,null);var child=comment(kind,root);var type=NotificationType.valueOf(kind.name()+"_COMMENT_REPLIED");
        var n=notification(type,child);var r=reader.response(n,context());assertTrue(r.available());assertEquals(12L,r.target().id());
        if(kind==ClubSourceKind.ANNOUNCEMENT){assertEquals(7L,r.target().announcementId());assertNull(r.target().eventId());assertNull(r.target().postId());}
        else {assertEquals(7L,r.target().eventId());assertNull(r.target().announcementId());}
        root.moderate(CommentModerationScope.PLATFORM,true,NOW);r=reader.response(n,context());assertFalse(r.available());assertNull(r.target());
        assertFalse(r.toString().contains("Comment text"));
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void privateMembershipLossAndSourceMismatchCloseTargets(ClubSourceKind kind)throws Exception{
        field(event,"audience",EventAudience.CLUB_MEMBERS);field(announcement,"audience",AnnouncementAudience.CLUB_MEMBERS);
        var n=notification(NotificationType.valueOf(kind.name()+"_COMMENTED"),comment(kind,null));
        assertTrue(reader.response(n,context()).available());field(membership,"status",ClubMembershipStatus.LEFT);
        assertFalse(reader.response(n,context()).available());field(membership,"status",ClubMembershipStatus.ACTIVE);
        field(n,"clubComment",comment(kind==ClubSourceKind.EVENT?ClubSourceKind.ANNOUNCEMENT:ClubSourceKind.EVENT,null));
        assertFalse(reader.response(n,context()).available());
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void newSocialLikesDoNotInheritAdminDraftVisibility(ClubSourceKind kind)throws Exception{
        actor.grantRole(Role.ADMIN);var n=notification(NotificationType.valueOf(kind.name()+"_LIKED"),null);
        assertTrue(reader.response(n,context()).available());field(event,"status",EventStatus.DRAFT);field(announcement,"status",AnnouncementStatus.DRAFT);
        assertFalse(reader.response(n,context()).available());
    }
    @Test void eventNormalReadAudienceIsBroaderThanAnnouncementRead()throws Exception{
        var ctx=new NotificationReader.Context(actor,null,Map.of());
        assertTrue(reader.response(notification(NotificationType.EVENT_LIKED,null),ctx).available());
        assertFalse(reader.response(notification(NotificationType.ANNOUNCEMENT_LIKED,null),ctx).available());
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void writerBindsAllValuesAndUsesReplyPriorityAndSeparateSourceKey(ClubSourceKind kind){
        var jdbc=mock(JdbcTemplate.class);var writer=new ClubCommentNotificationWriter(jdbc,Clock.fixed(NOW,ZoneOffset.UTC));
        writer.created(comment(kind,null),actor);
        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);verify(jdbc).update(sql.capture(),args.capture());
        assertEquals(sql.getValue().chars().filter(c->c=='?').count(),args.getValue().length);
        assertTrue(sql.getValue().contains("distinct on(user_id)"));assertTrue(sql.getValue().contains("priority desc"));
        assertTrue(Arrays.asList(args.getValue()).contains("CLUB_COMMENT_CREATED:11"));
        assertTrue(Arrays.asList(args.getValue()).contains(kind.name()+"_COMMENT_REPLIED"));
        assertFalse(sql.getValue().contains("Comment text"));assertTrue(sql.getValue().contains("on conflict(recipient_id,source_key) do nothing"));
        assertTrue(sql.getValue().contains("recipient_id"));assertTrue(sql.getValue().contains("r.user_id<>?"));
    }
    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void likeWriterDeduplicatesRecipientsBeforePayloadAndKeepsStableKey(ClubSourceKind kind){
        for(boolean comment:new boolean[]{false,true}){
            var jdbc=mock(JdbcTemplate.class);var writer=new ClubCommentNotificationWriter(jdbc,Clock.fixed(NOW,ZoneOffset.UTC));
            if(comment){var like=new ClubCommentLike(comment(kind,null),actor,NOW);field(like,"id",13L);writer.commentLiked(like,actor);}
            else {var like=new ClubContentLike(kind==ClubSourceKind.EVENT?event:null,kind==ClubSourceKind.ANNOUNCEMENT?announcement:null,actor,NOW);field(like,"id",13L);writer.sourceLiked(like,actor);}
            var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);verify(jdbc).update(sql.capture(),args.capture());
            assertEquals(sql.getValue().chars().filter(c->c=='?').count(),args.getValue().length);
            assertTrue(sql.getValue().indexOf("select distinct user_id")<sql.getValue().indexOf("insert into"));
            assertTrue(Arrays.asList(args.getValue()).contains(kind.name()+(comment?"_COMMENT_LIKED:13":"_LIKED:13")));
            assertFalse(sql.getValue().contains("select distinct r.user_id"));
        }
    }
    @Test void entitySourcesAreExclusiveAndRemovedPlaceholderDoesNotExposeAuthorOrBody(){
        assertThrows(IllegalArgumentException.class,()->new ClubComment(null,null,actor,null,"Body",NOW));
        assertThrows(IllegalArgumentException.class,()->new ClubComment(event,announcement,actor,null,"Body",NOW));
        assertThrows(IllegalArgumentException.class,()->new ClubContentLike(event,announcement,actor,NOW));
        assertThrows(IllegalArgumentException.class,()->new ClubContentLike(null,null,actor,NOW));
        var row=new ClubCommentRow(1L,7L,null,null,1L,"Name","Removed text",CommentStatus.REMOVED,false,false,NOW,NOW,NOW,1L);
        var response=row.response(2,4,true);assertNull(response.author());assertNull(response.body());assertEquals(0,response.likeCount());assertFalse(response.likedByMe());
        assertFalse(row.toString().contains("Removed text"));
    }
}
