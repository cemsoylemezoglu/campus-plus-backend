package com.campusplus.backend.service.post;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.PostModerationRepository;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly = true) @PreAuthorize("isAuthenticated()")
public class PostQueryService {
    private final PostWorkspace w;
    private final PostModerationRepository audit;
    public PostQueryService(PostWorkspace w, PostModerationRepository audit) { this.w = w; this.audit = audit; }
    public FeedResponse feed(@Positive Long clubId, @Size(max = 100) String cursor, @Min(1) @Max(100) int size) {
        User actor = w.core.actor(); StudentProfile profile = w.core.profiles.findById(actor.getId())
                .orElseThrow(() -> new ResourceConflictException("Öğrenci profilinizi tamamlayınız."));
        if (!profile.getUniversity().isActive()) throw new ResourceConflictException("Üniversite aktif değil.");
        FeedCursor before = FeedCursor.decode(cursor, clubId);
        var paging = PageRequest.of(0, size + 1);
        var rows = before == null
                ? w.posts.feedFirst(actor.getId(), profile.getUniversity().getId(), clubId, paging)
                : w.posts.feed(actor.getId(), profile.getUniversity().getId(), clubId, before.time(), before.id(), paging);
        boolean more = rows.size() > size; var items = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (more) { var last = items.getLast(); next = new FeedCursor(last.publishedAt(), last.id(), clubId).encode(); }
        return new FeedResponse(items.stream().map(PostRow::summary).toList(), next, more);
    }
    public PostResponse get(@Positive Long id) {
        Long userId = w.core.current.userId(); var profile = w.core.profiles.findById(userId);
        if (profile.isEmpty()) throw new ResourceNotFoundException("Gönderi bulunamadı.");
        PostRow row = w.posts.visible(id, userId, profile.get().getUniversity().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı."));
        return new PostResponse(row.summary(), row.body());
    }
    public ManagedPostResponse ownDetail(@Positive Long id) { return ManagedPostResponse.from(w.own(id)); }
    public ManagedPostResponse clubDetail(@Positive Long clubId, @Positive Long id) {
        w.preflightClub(clubId); return ManagedPostResponse.from(w.clubPost(clubId, id));
    }
    public PageResponse<ManagedPostResponse> own(PostStatus status, @Min(0) int page, @Min(1) @Max(100) int size) {
        return PageResponse.from(w.posts.own(w.core.current.userId(), status, page(page, size)).map(PostRow::managed));
    }
    public PageResponse<ManagedPostResponse> club(@Positive Long clubId, PostStatus status, @Min(0) int page, @Min(1) @Max(100) int size) {
        w.preflightClub(clubId); return PageResponse.from(w.posts.managed(clubId, status, page(page, size)).map(PostRow::managed));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminPostResponse> moderation(PostStatus status, Boolean hidden, @Positive Long universityId,
            @Positive Long clubId, @Min(0) int page, @Min(1) @Max(100) int size) {
        w.core.policy.requireAdmin(w.core.actor());
        return PageResponse.from(w.posts.moderation(status, hidden, universityId, clubId, page(page, size)).map(PostRow::admin));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public AdminPostResponse adminDetail(@Positive Long id) {
        w.core.policy.requireAdmin(w.core.actor()); return AdminPostResponse.from(w.post(id));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<PostModerationResponse> history(@Positive Long id, @Min(0) int page, @Min(1) @Max(100) int size) {
        w.core.policy.requireAdmin(w.core.actor()); w.post(id);
        return PageResponse.from(audit.history(id, page(page, size)));
    }
    private Pageable page(int page, int size) { return PageRequest.of(page, size, Sort.by("id").descending()); }
}
