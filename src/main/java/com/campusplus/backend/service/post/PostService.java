package com.campusplus.backend.service.post;

import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class PostService {
    private final PostWorkspace w;
    public PostService(PostWorkspace w) { this.w = w; }
    public ManagedPostResponse createStudent(@Valid CreateStudentPostRequest request) {
        var ctx = w.createStudent();
        return response(new Post(ctx.university(), ctx.actor(), null, request.body(), PostAudience.UNIVERSITY_STUDENTS, w.core.clock.instant()));
    }
    public ManagedPostResponse createClub(@Positive Long clubId, @Valid CreateClubPostRequest request) {
        var ctx = w.createClub(clubId);
        return response(new Post(ctx.university(), ctx.actor(), ctx.club(), request.body(), request.audience(), w.core.clock.instant()));
    }
    public ManagedPostResponse update(@Positive Long clubId, @Positive Long id, @Valid UpdatePostRequest request) {
        var ctx = w.write(clubId, id, false); Post post = ctx.post();
        ClubWorkspace.version(post.getVersion(), request.version()); w.policy.requireEditable(post);
        PostAudience audience = request.audience() == null ? post.getAudience() : request.audience();
        w.policy.requireAudience(post, audience); post.edit(request.body(), audience, w.core.clock.instant());
        return response(post);
    }
    public ManagedPostResponse publish(@Positive Long clubId, @Positive Long id, @Valid PostActionRequest request) {
        var ctx = w.write(clubId, id, false); Post post = ctx.post();
        ClubWorkspace.version(post.getVersion(), request.version()); w.policy.requireEditable(post);
        if (post.getStatus() != PostStatus.DRAFT) throw new ResourceConflictException("Gönderi zaten yayımlanmış.");
        post.publish(w.core.clock.instant()); return response(post);
    }
    public ManagedPostResponse remove(@Positive Long clubId, @Positive Long id, @Valid PostReasonRequest request) {
        var ctx = w.write(clubId, id, true); Post post = ctx.post();
        ClubWorkspace.version(post.getVersion(), request.version());
        if (post.getStatus() == PostStatus.REMOVED) throw new ResourceConflictException("Gönderi zaten kaldırılmış.");
        post.remove(ctx.actor(), request.reason(), w.core.clock.instant()); return response(post);
    }
    private ManagedPostResponse response(Post post) { return ManagedPostResponse.from(w.posts.saveAndFlush(post)); }
}
