package com.campusplus.backend.service.notification;
import com.campusplus.backend.dto.notification.NotificationResponse;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Resolves references in batches; notification ownership never grants source permissions. */
@Component
public class NotificationReader {
    private final ClubWorkspace core;
    private final AnnouncementPolicy announcements;
    private final EventPolicy events;
    public NotificationReader(ClubWorkspace core,AnnouncementPolicy announcements,EventPolicy events){this.core=core;this.announcements=announcements;this.events=events;}
    public record Context(User actor,StudentProfile profile,Map<Long,ClubMembership> memberships) {}
    public Context context(List<Notification> values) {
        User actor=core.actor();var ids=values.stream().map(Notification::getClub).filter(Objects::nonNull).map(Club::getId).distinct().toList();
        Map<Long,ClubMembership> local=ids.isEmpty()?Map.of():core.memberships.findByClubIdInAndUserId(ids,actor.getId()).stream()
            .collect(Collectors.toMap(m -> m.getClub().getId(),Function.identity()));
        return new Context(actor,core.profiles.findById(actor.getId()).orElse(null),local);
    }
    public NotificationResponse response(Notification n,Context ctx) {
        if(!Objects.equals(n.getRecipientId(),ctx.actor().getId()))throw new ResourceNotFoundException("Bildirim bulunamadı.");
        Club c=n.getClub();ClubMembership local=c==null?null:ctx.memberships().get(c.getId());
        Long source=null;Long eventId=null;
        if(c!=null)switch(n.getType().resource()) {
            case CLUB -> {if(core.policy.visible(c,ctx.actor(),local))source=c.getId();}
            case CLUB_MEMBERSHIP -> {
                ClubMembership m=n.getMembership();
                if(m!=null&&m.getClub().getId().equals(c.getId())
                    &&(m.getUserId().equals(ctx.actor().getId())||core.policy.admin(ctx.actor())||core.policy.manager(ctx.actor(),local)))source=m.getId();
            }
            case ANNOUNCEMENT -> {
                Announcement a=n.getAnnouncement();
                if(a!=null&&a.getClub().getId().equals(c.getId())&&announcements.visible(a,ctx.actor(),local,ctx.profile()))source=a.getId();
            }
            case EVENT -> {
                Event e=n.getEvent();
                if(e!=null&&e.getClub().getId().equals(c.getId())&&events.visible(e,ctx.actor(),local)){source=e.getId();eventId=e.getId();}
            }
            case EVENT_PARTICIPATION -> {
                EventParticipation p=n.getParticipation();Event e=n.getEvent();
                // The existing own-participations API permits personal history even after membership ends.
                if(p!=null&&e!=null&&p.getUserId().equals(ctx.actor().getId())&&p.getEvent().getId().equals(e.getId())
                    &&e.getClub().getId().equals(c.getId())){source=p.getId();eventId=e.getId();}
            }
        }
        boolean available=source!=null;
        return new NotificationResponse(n.getId(),n.getType(),n.getCreatedAt(),n.getReadAt(),n.getVersion(),available,
            available?n.getType().message():"Bu bildirimin içeriğine artık erişilemiyor.",
            available?new NotificationResponse.Target(n.getType().resource(),source,c.getId(),eventId):null);
    }
}
