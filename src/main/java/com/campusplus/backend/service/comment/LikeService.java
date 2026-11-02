package com.campusplus.backend.service.comment;
import com.campusplus.backend.dto.comment.LikeState;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.service.notification.CommentNotificationWriter;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class LikeService {
    private final CommentWorkspace w;
    private final PostLikeRepository posts;
    private final PostCommentLikeRepository comments;
    private final CommentNotificationWriter notifications;
    private final EntityManager em;
    public LikeService(CommentWorkspace w,PostLikeRepository posts,PostCommentLikeRepository comments,CommentNotificationWriter notifications,EntityManager em) {
        this.w=w;this.posts=posts;this.comments=comments;this.notifications=notifications;this.em=em;
    }
    public LikeState post(@Positive Long id) {
        var ctx=w.participant(id);var existing=posts.findByPostIdAndUserId(id,ctx.actor().getId());
        if(existing.isPresent()) {var row=existing.get();if(!row.isActive()){row.setActive(true,w.posts.core.clock.instant());posts.saveAndFlush(row);}}
        else {var row=posts.saveAndFlush(new PostLike(ctx.post(),ctx.actor(),w.posts.core.clock.instant()));notifications.postLiked(row,ctx.actor());}
        return new LikeState(true);
    }
    public LikeState comment(@Positive Long postId,@Positive Long id) {
        var ctx=w.participant(postId);var c=w.lock(id,postId);w.policy.visible(c);
        var existing=comments.findByCommentIdAndUserId(id,ctx.actor().getId());
        if(existing.isPresent()){var row=existing.get();if(!row.isActive()){row.setActive(true,w.posts.core.clock.instant());comments.saveAndFlush(row);}}
        else {var row=comments.saveAndFlush(new PostCommentLike(c,ctx.actor(),w.posts.core.clock.instant()));notifications.commentLiked(row,ctx.actor());}
        return new LikeState(true);
    }
    public void unlikePost(@Positive Long id) {
        Long actor=w.posts.core.current.userId();var previous=posts.findByPostIdAndUserId(id,actor);
        if(previous.isEmpty())return;
        w.posts.lockInteraction(id);var row=posts.findByPostIdAndUserId(id,actor).orElseThrow();em.refresh(row);
        if(row.isActive()){row.setActive(false,w.posts.core.clock.instant());posts.saveAndFlush(row);}
    }
    public void unlikeComment(@Positive Long postId,@Positive Long id) {
        Long actor=w.posts.core.current.userId();var previous=comments.findByCommentIdAndUserId(id,actor);
        if(previous.isEmpty()||!previous.get().getComment().getPost().getId().equals(postId))return;
        w.posts.lockInteraction(postId);w.lock(id,postId);var row=comments.findByCommentIdAndUserId(id,actor).orElseThrow();em.refresh(row);
        if(row.isActive()){row.setActive(false,w.posts.core.clock.instant());comments.saveAndFlush(row);}
    }
}
