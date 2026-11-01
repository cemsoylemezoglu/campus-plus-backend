package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.*;
import java.time.Instant;

public record PostSummary(Long id, PostPublisher publisher, Identity university, Identity author,
        Identity club, String excerpt, Instant publishedAt, Instant updatedAt, Long version) {
    public record Identity(Long id, String name) {}
    public static PostSummary from(Post p) {
        String body = p.getBody();
        // Code point boundary: do not split an emoji surrogate pair in a preview.
        int end = body.offsetByCodePoints(0, Math.min(280, body.codePointCount(0, body.length())));
        return new PostSummary(p.getId(), p.getPublisher(), new Identity(p.getUniversity().getId(), p.getUniversity().getName()),
                p.getPublisher() == PostPublisher.STUDENT ? new Identity(p.getCreatedBy().getId(), p.getCreatedBy().getName()) : null,
                p.getClub() == null ? null : new Identity(p.getClub().getId(), p.getClub().getName()),
                body.substring(0, end), p.getPublishedAt(), p.getUpdatedAt(), p.getVersion());
    }
    @Override public String toString() { return "PostSummary[id=" + id + ",content=[REDACTED]]"; }
}
