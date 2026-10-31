package com.campusplus.backend.service.notification;
import com.campusplus.backend.entity.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Synchronous durable inbox writes in the source transaction; no external delivery or per-recipient user lock. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class NotificationWriter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public NotificationWriter(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    private static final String INSERT="insert into public.notifications(recipient_id,actor_id,type,source_key,club_id,announcement_id,event_id,participation_id,membership_id,created_at,version) select ";
    private static final String DEDUPE=" on conflict(recipient_id,source_key) do nothing";
    private String key(NotificationType type,Long id,Long version){return type.name()+":"+Objects.requireNonNull(id)+":"+Objects.requireNonNull(version);}
    private Timestamp now(){return Timestamp.from(clock.instant());}
    private void broadcast(Club club,User actor,NotificationType type,String key,Long announcement,Long event) {
        jdbc.update(INSERT+"m.user_id,?,?,?,m.club_id,?,?,null,null,?,0 from public.club_memberships m "
            +"join public.student_profiles s on s.user_id=m.user_id join public.clubs c on c.id=m.club_id "
            +"join public.universities u on u.id=c.university_id where m.club_id=? and m.status='ACTIVE' "
            +"and s.university_id=c.university_id and c.status='ACTIVE' and u.active=true and m.user_id<>?"+DEDUPE,
            actor.getId(),type.name(),key,announcement,event,now(),club.getId(),actor.getId());
    }
    public void announcementPublished(Announcement a,User actor) {
        broadcast(a.getClub(),actor,NotificationType.ANNOUNCEMENT_PUBLISHED,key(NotificationType.ANNOUNCEMENT_PUBLISHED,a.getId(),a.getVersion()),a.getId(),null);
    }
    public void eventPublished(Event e,User actor) {
        broadcast(e.getClub(),actor,NotificationType.EVENT_PUBLISHED,key(NotificationType.EVENT_PUBLISHED,e.getId(),e.getVersion()),null,e.getId());
    }
    public void clubChanged(Club c,User actor,NotificationType type) {
        if(type.resource()!=NotificationResource.CLUB)throw new IllegalArgumentException("Club notification type required");
        boolean owner=type==NotificationType.CLUB_APPROVED||type==NotificationType.CLUB_REJECTED;
        jdbc.update(INSERT+"m.user_id,?,?,?,m.club_id,null,null,null,null,?,0 from public.club_memberships m "
            +"where m.club_id=? and m.status='ACTIVE'"+(owner?" and m.role='OWNER'":"")+DEDUPE,
            actor.getId(),type.name(),key(type,c.getId(),c.getVersion()),now(),c.getId());
    }
    public void membershipChanged(ClubMembership m,User actor,NotificationType type) {
        if(type.resource()!=NotificationResource.CLUB_MEMBERSHIP)throw new IllegalArgumentException("Membership notification type required");
        String recipients=type==NotificationType.MEMBERSHIP_REQUESTED
            ?"select managers.user_id from public.club_memberships managers join public.user_roles r on r.user_id=managers.user_id and r.role='CLUB_ADMIN' where managers.club_id=? and managers.status='ACTIVE' and managers.role in ('OWNER','MANAGER')"
            :"select user_id from public.club_memberships where id=?";
        jdbc.update(INSERT+"recipients.user_id,?,?,?,?,null,null,null,?,?,0 from ("+recipients+") recipients"+DEDUPE,
            actor.getId(),type.name(),key(type,m.getId(),m.getVersion()),m.getClub().getId(),m.getId(),now(),
            type==NotificationType.MEMBERSHIP_REQUESTED?m.getClub().getId():m.getId());
    }
    public void eventChanged(Event e,User actor,NotificationType type) {
        if(type!=NotificationType.EVENT_RESCHEDULED&&type!=NotificationType.EVENT_CANCELLED)throw new IllegalArgumentException("Event change notification type required");
        jdbc.update(INSERT+"p.user_id,?,?,?, ?,null,?,null,null,?,0 from public.event_participations p "
            +"where p.event_id=? and p.status in ('REGISTERED','ATTENDED')"+DEDUPE,
            actor.getId(),type.name(),key(type,e.getId(),e.getVersion()),e.getClub().getId(),e.getId(),now(),e.getId());
    }
    public void participationChanged(EventParticipation p,User actor,NotificationType type) {
        if(type.resource()!=NotificationResource.EVENT_PARTICIPATION)throw new IllegalArgumentException("Participation notification type required");
        jdbc.update(INSERT+"p.user_id,?,?,?,?,null,p.event_id,p.id,null,?,0 from public.event_participations p where p.id=?"+DEDUPE,
            actor.getId(),type.name(),key(type,p.getId(),p.getVersion()),p.getEvent().getClub().getId(),now(),p.getId());
    }
    /** Called before the matching endRegistered bulk update, with the event lock held. */
    public void participationsEnding(Event e,Long userId,User actor) {
        String type=NotificationType.PARTICIPATION_CANCELLED.name();
        var args=new ArrayList<Object>(List.of(actor.getId(),type,type+":",e.getClub().getId(),now(),e.getId()));
        if(userId!=null)args.add(userId);
        jdbc.update(INSERT+"p.user_id,?,?,? || cast(p.id as varchar) || ':' || cast(p.version+1 as varchar),?,null,p.event_id,p.id,null,?,0 "
            +"from public.event_participations p where p.event_id=? and p.status='REGISTERED'"+(userId==null?"":" and p.user_id=?")+DEDUPE,args.toArray());
    }
    public void attendedEventArchived(Event e,User actor) {
        var type=NotificationType.EVENT_CANCELLED;
        jdbc.update(INSERT+"p.user_id,?,?,?,?,null,p.event_id,null,null,?,0 from public.event_participations p where p.event_id=? and p.status='ATTENDED'"+DEDUPE,
            actor.getId(),type.name(),key(type,e.getId(),e.getVersion()),e.getClub().getId(),now(),e.getId());
    }
}
