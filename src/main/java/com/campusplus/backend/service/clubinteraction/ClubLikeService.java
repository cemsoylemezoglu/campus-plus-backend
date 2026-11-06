package com.campusplus.backend.service.clubinteraction;

import com.campusplus.backend.dto.clubinteraction.ClubSourceRef;
import com.campusplus.backend.dto.comment.LikeState;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.service.notification.ClubCommentNotificationWriter;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class ClubLikeService {
    private final ClubCommentWorkspace w;
    private final ClubContentLikeRepository likes;
    private final ClubCommentLikeRepository commentLikes;
    private final ClubCommentNotificationWriter notifications;
    private final EntityManager em;
    public ClubLikeService(ClubCommentWorkspace w,ClubContentLikeRepository likes,ClubCommentLikeRepository commentLikes,
            ClubCommentNotificationWriter notifications,EntityManager em) {
        this.w=w;this.likes=likes;this.commentLikes=commentLikes;this.notifications=notifications;this.em=em;
    }
    public LikeState source(ClubSourceRef ref) {
        var ctx=w.sources.participant(ref);
        var existing=likes.own(ref.eventId(),ref.announcementId(),ctx.actor().getId());
        if(existing.isPresent()) {
            var row=existing.get();em.refresh(row);
            if(!row.isActive()){row.setActive(true,w.core.clock.instant());likes.saveAndFlush(row);}
        } else {
            var row=likes.saveAndFlush(new ClubContentLike(ctx.source().event(),ctx.source().announcement(),ctx.actor(),w.core.clock.instant()));
            notifications.sourceLiked(row,ctx.actor());
        }
        return new LikeState(true);
    }
    public LikeState comment(ClubSourceRef ref,@Positive Long id) {
        var ctx=w.sources.participant(ref);var comment=w.lock(id,ref);w.visible(comment);
        var existing=commentLikes.findByCommentIdAndUserId(id,ctx.actor().getId());
        if(existing.isPresent()) {
            var row=existing.get();em.refresh(row);
            if(!row.isActive()){row.setActive(true,w.core.clock.instant());commentLikes.saveAndFlush(row);}
        } else {
            var row=commentLikes.saveAndFlush(new ClubCommentLike(comment,ctx.actor(),w.core.clock.instant()));
            notifications.commentLiked(row,ctx.actor());
        }
        return new LikeState(true);
    }
    public void unlikeSource(ClubSourceRef ref) {
        long actor=w.core.current.userId();
        if(likes.own(ref.eventId(),ref.announcementId(),actor).isEmpty())return;
        // Only this actor's existing like authorizes cleanup; ordinary visibility is not required.
        w.sources.cleanup(ref);
        var row=likes.own(ref.eventId(),ref.announcementId(),actor).orElseThrow();em.refresh(row);
        if(row.isActive()){row.setActive(false,w.core.clock.instant());likes.saveAndFlush(row);}
    }
    public void unlikeComment(ClubSourceRef ref,@Positive Long id) {
        long actor=w.core.current.userId();var previous=commentLikes.findByCommentIdAndUserId(id,actor);
        if(previous.isEmpty()||!previous.get().getComment().source().equals(ref))return;
        w.sources.cleanup(ref);w.lock(id,ref);
        var row=commentLikes.findByCommentIdAndUserId(id,actor).orElseThrow();em.refresh(row);
        if(row.isActive()){row.setActive(false,w.core.clock.instant());commentLikes.saveAndFlush(row);}
    }
}
