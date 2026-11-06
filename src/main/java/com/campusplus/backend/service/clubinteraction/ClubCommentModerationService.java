package com.campusplus.backend.service.clubinteraction;
import com.campusplus.backend.dto.clubinteraction.*;

import com.campusplus.backend.dto.post.PostReasonRequest;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.repository.ClubCommentModerationRepository;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class ClubCommentModerationService {
    private final ClubCommentWorkspace w;
    private final ClubCommentModerationRepository audit;
    public ClubCommentModerationService(ClubCommentWorkspace w,ClubCommentModerationRepository audit){this.w=w;this.audit=audit;}
    public ClubCommentModerationResult resource(ClubSourceRef ref,@Positive Long id,boolean hidden,@Valid PostReasonRequest request) {
        var ctx=w.sources.resourceManager(ref);
        var c=w.lock(id,ref);apply(c,ctx.actor(),CommentModerationScope.RESOURCE,hidden,request);return ClubCommentModerationResult.from(c);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public OwnClubCommentResponse platform(@Positive Long id,boolean hidden,@Valid PostReasonRequest request) {
        w.core.policy.requireAdmin(w.core.actor());var preview=w.comment(id);
        var ctx=w.sources.cleanup(preview.source());w.core.policy.requireAdmin(ctx.actor());
        return apply(w.lock(id,ctx.source().ref()),ctx.actor(),CommentModerationScope.PLATFORM,hidden,request);
    }
    private OwnClubCommentResponse apply(ClubComment c,User actor,CommentModerationScope scope,boolean hidden,PostReasonRequest request) {
        ClubWorkspace.version(c.getVersion(),request.version());
        if(c.getStatus()==CommentStatus.REMOVED&&c.getRoot()!=null)throw new ResourceConflictException("Kaldırılmış yanıt değiştirilemez.");
        boolean current=scope==CommentModerationScope.PLATFORM?c.isPlatformHidden():c.isResourceHidden();
        if(current==hidden)throw new ResourceConflictException("Moderasyon durumu zaten bu değerde.");
        var now=w.core.clock.instant();c.moderate(scope,hidden,now);w.comments.saveAndFlush(c);
        audit.save(new ClubCommentModeration(c,actor,scope,hidden,request.reason(),now));audit.flush();return OwnClubCommentResponse.from(c);
    }
}
