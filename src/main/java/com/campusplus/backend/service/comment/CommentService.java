package com.campusplus.backend.service.comment;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.dto.post.PostActionRequest;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.notification.CommentNotificationWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class CommentService {
    private final CommentWorkspace w;
    private final CommentNotificationWriter notifications;
    public CommentService(CommentWorkspace w,CommentNotificationWriter notifications){this.w=w;this.notifications=notifications;}
    public OwnCommentResponse create(@Positive Long postId,@Positive Long rootId,@Valid CreateCommentRequest request) {
        var ctx=w.participant(postId);PostComment root=rootId==null?null:w.lock(rootId,postId);
        if(root!=null)w.policy.root(root);
        PostComment c=w.comments.saveAndFlush(new PostComment(ctx.post(),ctx.actor(),root,request.body(),w.posts.core.clock.instant()));
        notifications.created(c,ctx.actor());return OwnCommentResponse.from(c);
    }
    public OwnCommentResponse update(@Positive Long postId,@Positive Long id,@Valid UpdateCommentRequest request) {
        var preview=w.comment(id);if(!preview.getPost().getId().equals(postId))w.policy.missing();w.policy.own(preview,w.posts.core.actor());
        var ctx=w.participant(postId);var c=w.lock(id,postId);w.policy.own(c,ctx.actor());w.policy.visible(c);
        ClubWorkspace.version(c.getVersion(),request.version());c.edit(request.body(),w.posts.core.clock.instant());
        return OwnCommentResponse.from(w.comments.saveAndFlush(c));
    }
    public OwnCommentResponse remove(@Positive Long id,@Valid PostActionRequest request) {
        PostComment preview=w.comment(id);w.policy.own(preview,w.posts.core.actor());
        var ctx=w.posts.lockInteraction(preview.getPost().getId());var c=w.lock(id,ctx.post().getId());w.policy.own(c,ctx.actor());
        ClubWorkspace.version(c.getVersion(),request.version());
        if(c.getStatus()==CommentStatus.REMOVED)throw new ResourceConflictException("Yorum zaten kaldırılmış.");
        c.remove(w.posts.core.clock.instant());return OwnCommentResponse.from(w.comments.saveAndFlush(c));
    }
}
