package com.campusplus.backend.service.comment;
import com.campusplus.backend.dto.comment.OwnCommentResponse;
import com.campusplus.backend.dto.comment.CommentModerationResult;
import com.campusplus.backend.dto.post.PostReasonRequest;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.repository.CommentModerationRepository;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class CommentModerationService {
    private final CommentWorkspace w;
    private final CommentModerationRepository audit;
    public CommentModerationService(CommentWorkspace w,CommentModerationRepository audit){this.w=w;this.audit=audit;}
    public CommentModerationResult resource(@Positive Long postId,@Positive Long id,boolean hidden,@Valid PostReasonRequest request) {
        w.resourceManager(w.posts.post(postId),w.posts.core.actor());
        var ctx=w.posts.lockInteraction(postId);w.resourceManager(ctx.post(),ctx.actor());
        var c=w.lock(id,postId);apply(c,ctx.actor(),CommentModerationScope.RESOURCE,hidden,request);return CommentModerationResult.from(c);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public OwnCommentResponse platform(@Positive Long id,boolean hidden,@Valid PostReasonRequest request) {
        w.posts.core.policy.requireAdmin(w.posts.core.actor());var preview=w.comment(id);
        var ctx=w.posts.lockInteraction(preview.getPost().getId());w.posts.core.policy.requireAdmin(ctx.actor());
        return apply(w.lock(id,ctx.post().getId()),ctx.actor(),CommentModerationScope.PLATFORM,hidden,request);
    }
    private OwnCommentResponse apply(PostComment c,User actor,CommentModerationScope scope,boolean hidden,PostReasonRequest request) {
        ClubWorkspace.version(c.getVersion(),request.version());
        if(c.getStatus()==CommentStatus.REMOVED&&c.getRoot()!=null)throw new ResourceConflictException("Kaldırılmış yanıt değiştirilemez.");
        boolean current=scope==CommentModerationScope.PLATFORM?c.isPlatformHidden():c.isResourceHidden();
        if(current==hidden)throw new ResourceConflictException("Moderasyon durumu zaten bu değerde.");
        var now=w.posts.core.clock.instant();c.moderate(scope,hidden,now);w.comments.saveAndFlush(c);
        audit.save(new CommentModeration(c,actor,scope,hidden,request.reason(),now));audit.flush();return OwnCommentResponse.from(c);
    }
}
