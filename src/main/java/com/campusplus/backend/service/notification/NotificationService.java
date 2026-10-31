package com.campusplus.backend.service.notification;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.notification.*;
import com.campusplus.backend.entity.Notification;
import com.campusplus.backend.exception.ResourceNotFoundException;
import com.campusplus.backend.repository.NotificationRepository;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
public class NotificationService {
    private final ClubWorkspace core;
    private final NotificationRepository notifications;
    private final NotificationReader reader;
    private final EntityManager em;
    public NotificationService(ClubWorkspace core,NotificationRepository notifications,NotificationReader reader,EntityManager em) {
        this.core=core;this.notifications=notifications;this.reader=reader;this.em=em;
    }
    public PageResponse<NotificationResponse> inbox(boolean unreadOnly,@Min(0) int page,@Min(1) @Max(100) int size) {
        var values=notifications.inbox(core.current.userId(),unreadOnly,PageRequest.of(page,size,Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        var ctx=reader.context(values.getContent());return PageResponse.from(values.map(n -> reader.response(n,ctx)));
    }
    public UnreadCountResponse unreadCount(){return new UnreadCountResponse(notifications.countByRecipientIdAndReadAtIsNull(core.current.userId()));}
    @Transactional
    public NotificationResponse read(@Positive Long id,@Valid NotificationReadRequest request) {
        Notification n=lock(id);
        if(n.getReadAt()==null){ClubWorkspace.version(n.getVersion(),request.version());n.read(core.clock.instant());notifications.saveAndFlush(n);}
        return reader.response(n,reader.context(List.of(n)));
    }
    @Transactional
    public NotificationResponse unread(@Positive Long id,@Valid NotificationReadRequest request) {
        Notification n=lock(id);ClubWorkspace.version(n.getVersion(),request.version());
        if(n.getReadAt()!=null){n.unread();notifications.saveAndFlush(n);}
        return reader.response(n,reader.context(List.of(n)));
    }
    @Transactional
    public ReadAllNotificationsResponse readAll(@Valid ReadAllNotificationsRequest request) {
        long actor=core.current.userId();core.lockUser(actor);
        return new ReadAllNotificationsResponse(notifications.readAll(actor,request.upToId(),core.clock.instant()));
    }
    private Notification lock(Long id) {
        long actor=core.current.userId();core.lockUser(actor);
        Notification n=notifications.findByIdAndRecipientId(id,actor).orElseThrow(() -> new ResourceNotFoundException("Bildirim bulunamadı."));
        em.refresh(n,LockModeType.PESSIMISTIC_WRITE);return n;
    }
}
