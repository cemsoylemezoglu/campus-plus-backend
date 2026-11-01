package com.campusplus.backend.dto.post;

import java.util.List;

public record FeedResponse(List<PostSummary> items, String nextCursor, boolean hasNext) {
    public FeedResponse { items = List.copyOf(items); }
}
