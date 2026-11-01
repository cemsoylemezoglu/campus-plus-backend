package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.Post;

public record PostResponse(PostSummary post, String body) {
    public static PostResponse from(Post p) { return new PostResponse(PostSummary.from(p), p.getBody()); }
    @Override public String toString() { return "PostResponse[content=[REDACTED]]"; }
}
