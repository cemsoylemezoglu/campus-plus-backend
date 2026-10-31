package com.campusplus.backend.service.event;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.service.notification.NotificationWriter;
import com.campusplus.backend.repository.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Internal synchronous coordinator. Callers already hold users -> club WRITE -> membership locks. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class EventLifecycleService {
    private final EventRepository events;
    private final EventParticipationRepository participants;
    private final NotificationWriter notifications;
    public EventLifecycleService(EventRepository events,EventParticipationRepository participants,NotificationWriter notifications) {this.events=events;this.participants=participants;this.notifications=notifications;}
    public void membershipEnded(Club club,Long userId,User actor,Instant now) {
        for(Event e:events.lockMemberEvents(club.getId(),userId,now)) {
            notifications.participationsEnding(e,userId,actor);
            participants.endRegistered(e.getId(),userId,EventParticipationStatus.CANCELLED,EventEndCause.MEMBERSHIP_ENDED,actor,null,now);
        }
    }
    public void clubArchived(Club club,User actor,Instant now) {
        for(Event e:events.lockOpenForClub(club.getId(),List.of(EventStatus.DRAFT,EventStatus.PUBLISHED))) {
            e.cancel(actor,"Kulüp arşivlendi.",now);events.saveAndFlush(e);
            if(e.getPublishedAt()!=null){notifications.participationsEnding(e,null,actor);notifications.attendedEventArchived(e,actor);}
            participants.endRegistered(e.getId(),null,EventParticipationStatus.CANCELLED,EventEndCause.CLUB_ARCHIVED,actor,null,now);
        }
    }
}
