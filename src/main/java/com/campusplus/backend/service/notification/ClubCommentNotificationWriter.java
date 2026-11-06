package com.campusplus.backend.service.notification;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Caller holds the source lock. Delivery participates in the source/comment transaction. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class ClubCommentNotificationWriter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ClubCommentNotificationWriter(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    private static final String INSERT="insert into public.notifications(recipient_id,actor_id,type,source_key,club_id,announcement_id,event_id,participation_id,membership_id,post_id,comment_id,club_comment_id,created_at,version) ";
    private static final String MANAGERS="select m.user_id from source s join public.club_memberships m on m.club_id=s.club_id "
        +"where m.status='ACTIVE' and m.role in ('OWNER','MANAGER') and exists(select from public.user_roles roles where roles.user_id=m.user_id and roles.role='CLUB_ADMIN')";
    private static final String EVENT="select e.id as event_id,cast(null as bigint) as announcement_id,e.club_id,e.audience from public.events e "
        +"join public.clubs c on c.id=e.club_id join public.universities u on u.id=c.university_id where e.id=? "
        +"and e.published_at is not null and e.archived_at is null and e.status in ('PUBLISHED','COMPLETED','CANCELLED') and c.status='ACTIVE' and u.active=true";
    private static final String ANNOUNCEMENT="select cast(null as bigint) as event_id,a.id as announcement_id,a.club_id,a.audience from public.club_announcements a "
        +"join public.clubs c on c.id=a.club_id join public.universities u on u.id=c.university_id where a.id=? "
        +"and a.published_at is not null and a.archived_at is null and a.status='PUBLISHED' and c.status='ACTIVE' and u.active=true";
    private static final String PROFILE="exists(select from public.student_profiles profile where profile.user_id=r.user_id and profile.university_id=club.university_id)";
    private static final String MEMBER="exists(select from public.club_memberships access where access.club_id=club.id and access.user_id=r.user_id and access.status='ACTIVE')";
    private String eligible(ClubSourceRef ref) {
        String audience="(s.audience='UNIVERSITY_STUDENTS' or ("+PROFILE+" and "+MEMBER+"))";
        return " join source s on true join public.clubs club on club.id=s.club_id where r.user_id<>? and "
                +(ref.kind()==ClubSourceKind.ANNOUNCEMENT?PROFILE+" and ":"")+audience;
    }
    private String source(ClubSourceRef ref){return ref.kind()==ClubSourceKind.EVENT?EVENT:ANNOUNCEMENT;}
    private String type(ClubSourceRef ref,String action){return ref.kind().name()+"_"+action;}
    public void created(ClubComment comment,User actor) {
        var ref=comment.source();Long id=Objects.requireNonNull(comment.getId());
        String sql="with source as ("+source(ref)+"), candidates as (select m.user_id,cast(? as varchar) as type,0 as priority from ("+MANAGERS+") m "
            +"union all select root.author_id,cast(? as varchar),1 from public.club_comments child join public.club_comments root on root.id=child.root_id where child.id=?), "
            +"recipients as (select distinct on(user_id) user_id,type from candidates order by user_id,priority desc) "
            +INSERT+"select r.user_id,?,r.type,?,s.club_id,s.announcement_id,s.event_id,null,null,null,null,cast(? as bigint),?,0 from recipients r"
            +eligible(ref)+" on conflict(recipient_id,source_key) do nothing";
        jdbc.update(sql,ref.id(),type(ref,"COMMENTED"),type(ref,"COMMENT_REPLIED"),id,actor.getId(),"CLUB_COMMENT_CREATED:"+id,id,Timestamp.from(clock.instant()),actor.getId());
    }
    public void sourceLiked(ClubContentLike like,User actor) {
        ClubSourceRef ref=like.getEvent()!=null?new ClubSourceRef(ClubSourceKind.EVENT,like.getEvent().getId())
            :new ClubSourceRef(ClubSourceKind.ANNOUNCEMENT,like.getAnnouncement().getId());
        liked(ref,null,Objects.requireNonNull(like.getId()),actor);
    }
    public void commentLiked(ClubCommentLike like,User actor) {
        liked(like.getComment().source(),like.getComment().getId(),Objects.requireNonNull(like.getId()),actor);
    }
    private void liked(ClubSourceRef ref,Long comment,Long like,User actor) {
        String type=type(ref,comment==null?"LIKED":"COMMENT_LIKED");
        String candidates=comment==null?MANAGERS:"select author_id as user_id from public.club_comments where id=?";
        String sql="with source as ("+source(ref)+"), candidates as ("+candidates+"), recipients as (select distinct user_id from candidates) "
            +INSERT+"select r.user_id,?,?,?,s.club_id,s.announcement_id,s.event_id,null,null,null,null,cast(? as bigint),?,0 from recipients r"
            +eligible(ref)+" on conflict(recipient_id,source_key) do nothing";
        var args=new ArrayList<Object>();args.add(ref.id());if(comment!=null)args.add(comment);
        args.add(actor.getId());args.add(type);args.add(type+":"+like);args.add(comment);args.add(Timestamp.from(clock.instant()));args.add(actor.getId());
        jdbc.update(sql,args.toArray());
    }
}
