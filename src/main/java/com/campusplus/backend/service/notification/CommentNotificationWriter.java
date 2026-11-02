package com.campusplus.backend.service.notification;
import com.campusplus.backend.entity.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Source lock held by caller; synchronous inbox insert in the same transaction. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class CommentNotificationWriter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CommentNotificationWriter(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    private static final String INSERT="insert into public.notifications(recipient_id,actor_id,type,source_key,club_id,announcement_id,event_id,participation_id,membership_id,post_id,comment_id,created_at,version) ";
    private static final String DEDUPE=" on conflict(recipient_id,source_key) do nothing";
    private static final String OWNERS="select p.created_by as user_id from public.posts p where p.id=? and p.publisher='STUDENT' union all "
        +"select m.user_id from public.posts p join public.club_memberships m on m.club_id=p.club_id "
        +"where p.id=? and m.status='ACTIVE' and m.role in ('OWNER','MANAGER') "
        +"and exists(select from public.user_roles roles where roles.user_id=m.user_id and roles.role='CLUB_ADMIN')";
    private static final String ELIGIBLE=" join public.student_profiles s on s.user_id=r.user_id join public.universities u on u.id=p.university_id "
        +"left join public.clubs club on club.id=p.club_id where r.user_id<>? and s.university_id=p.university_id and u.active=true "
        +"and p.status='PUBLISHED' and p.moderation_hidden=false and (club.id is null or club.status='ACTIVE') "
        +"and (p.audience='UNIVERSITY_STUDENTS' or exists(select from public.club_memberships membership_access where membership_access.club_id=p.club_id "
        +"and membership_access.user_id=r.user_id and membership_access.status='ACTIVE'))";
    public void created(PostComment comment,User actor) {
        Long post=Objects.requireNonNull(comment.getPost().getId()),id=Objects.requireNonNull(comment.getId());
        String sql="with candidates as (select owners.user_id,'POST_COMMENTED' as type,0 as priority from ("+OWNERS+") owners "
            +"union all select root.author_id,'COMMENT_REPLIED',1 from public.post_comments child join public.post_comments root on root.id=child.root_id "
            +"where child.id=?), recipients as (select distinct on(user_id) user_id,type from candidates order by user_id,priority desc) "
            +INSERT+"select r.user_id,?,r.type,?,p.club_id,null,null,null,null,p.id,x.id,?,0 from recipients r "
            +"join public.posts p on p.id=? join public.post_comments x on x.id=? and x.post_id=p.id"+ELIGIBLE+DEDUPE;
        jdbc.update(sql,post,post,id,actor.getId(),"COMMENT_CREATED:"+id,Timestamp.from(clock.instant()),post,id,actor.getId());
    }
    public void postLiked(PostLike like,User actor){liked(like.getPost(),null,like.getId(),actor,NotificationType.POST_LIKED);}
    public void commentLiked(PostCommentLike like,User actor){liked(like.getComment().getPost(),like.getComment().getId(),like.getId(),actor,NotificationType.COMMENT_LIKED);}
    private void liked(Post post,Long comment,Long likeId,User actor,NotificationType type) {
        Objects.requireNonNull(likeId);var args=new ArrayList<Object>();String recipients;
        if(comment==null){recipients=OWNERS;args.add(post.getId());args.add(post.getId());}
        else {recipients="select author_id as user_id from public.post_comments where id=?";args.add(comment);}
        args.add(actor.getId());args.add(type.name());args.add(type.name()+":"+likeId);args.add(comment);
        args.add(Timestamp.from(clock.instant()));args.add(post.getId());args.add(actor.getId());
        // Deduplicate recipients before INSERT: DISTINCT on the payload resolves bare NULLs as text in PostgreSQL.
        jdbc.update(INSERT+"select r.user_id,?,?,?,p.club_id,null,null,null,null,p.id,cast(? as bigint),?,0 from (select distinct user_id from ("+recipients+") candidates) r "
            +"join public.posts p on p.id=?"+ELIGIBLE+DEDUPE,reorderLikeArgs(args,comment==null?2:1));
    }
    // SELECT placeholders occur before the recipient subquery placeholders in JDBC order.
    private Object[] reorderLikeArgs(List<Object> args,int recipientParameters) {
        var ordered=new ArrayList<Object>();ordered.addAll(args.subList(recipientParameters,recipientParameters+5));
        ordered.addAll(args.subList(0,recipientParameters));ordered.addAll(args.subList(recipientParameters+5,args.size()));
        return ordered.toArray();
    }
}
