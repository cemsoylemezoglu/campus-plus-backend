package com.campusplus.backend.service.post;

import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.repository.PostModerationRepository;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional @PreAuthorize("hasRole('ADMIN')")
public class PostModerationService {
    private final PostWorkspace w;
    private final PostModerationRepository audit;
    public PostModerationService(PostWorkspace w, PostModerationRepository audit) { this.w = w; this.audit = audit; }
    public AdminPostResponse moderate(@Positive Long id, boolean hidden, @Valid PostReasonRequest request) {
        var ctx = w.moderate(id); Post post = ctx.post();
        ClubWorkspace.version(post.getVersion(), request.version());
        if (post.isModerationHidden() == hidden) throw new ResourceConflictException("Moderasyon durumu zaten bu değerde.");
        if (post.getStatus() == PostStatus.REMOVED) throw new ResourceConflictException("Kaldırılmış gönderiye moderasyon uygulanamaz.");
        var now = w.core.clock.instant(); post.moderate(hidden, now); w.posts.saveAndFlush(post);
        audit.save(new PostModeration(post, ctx.actor(), hidden ? PostModerationAction.HIDE : PostModerationAction.RESTORE, request.reason(), now));
        audit.flush(); return AdminPostResponse.from(post);
    }
}
