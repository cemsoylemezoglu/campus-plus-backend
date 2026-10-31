package com.campusplus.backend.service.announcement;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.AnnouncementRepository;
import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Internal coordinator; caller holds users -> club WRITE and has completed event side effects. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class AnnouncementLifecycleService {
    private final AnnouncementRepository announcements;
    private final EntityManager em;
    public AnnouncementLifecycleService(AnnouncementRepository announcements,EntityManager em){this.announcements=announcements;this.em=em;}
    public void clubArchived(Club club,User actor,Instant now) {
        for(Announcement a:announcements.lockUnarchived(club.getId())) {
            em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
            if(a.getStatus()==AnnouncementStatus.PUBLISHED)a.withdraw(actor,"Kulüp arşivlendi.",now);
            a.archive(now);announcements.saveAndFlush(a);
        }
    }
}
