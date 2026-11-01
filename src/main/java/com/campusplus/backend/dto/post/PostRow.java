package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.*;
import java.time.Instant;

/** Scalar projection: paging never fetches a User and its eager role collection. */
public record PostRow(Long id, Long universityId, String universityName, Long authorId, String authorName,
        Long clubId, String clubName, PostPublisher publisher, PostAudience audience, PostStatus status,
        String body, Instant publishedAt, Instant updatedAt, Instant createdAt, Instant removedAt,
        Long removedBy, String removalReason, boolean moderationHidden, Long version) {
    public PostSummary summary() {
        int end = body.offsetByCodePoints(0, Math.min(280, body.codePointCount(0, body.length())));
        return new PostSummary(id, publisher, new PostSummary.Identity(universityId, universityName),
                publisher == PostPublisher.STUDENT ? new PostSummary.Identity(authorId, authorName) : null,
                clubId == null ? null : new PostSummary.Identity(clubId, clubName), body.substring(0, end), publishedAt, updatedAt, version);
    }
    public ManagedPostResponse managed() {
        return new ManagedPostResponse(new PostResponse(summary(), body), status, audience, moderationHidden, createdAt, removedAt, removalReason);
    }
    public AdminPostResponse admin() { return new AdminPostResponse(managed(), authorId, removedBy); }
    @Override public String toString() { return "PostRow[id=" + id + ",content=[REDACTED]]"; }
}
