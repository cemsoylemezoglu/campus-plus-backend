package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.Post;

public record AdminPostResponse(ManagedPostResponse post, Long createdBy, Long removedBy) {
    public static AdminPostResponse from(Post p) {
        return new AdminPostResponse(ManagedPostResponse.from(p), p.getCreatedBy().getId(),
                p.getRemovedBy() == null ? null : p.getRemovedBy().getId());
    }
    @Override public String toString() { return "AdminPostResponse[content=[REDACTED]]"; }
}
