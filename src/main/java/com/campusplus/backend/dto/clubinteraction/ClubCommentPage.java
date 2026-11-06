package com.campusplus.backend.dto.clubinteraction;
import java.util.List;
public record ClubCommentPage(List<ClubCommentResponse> items,String nextCursor,boolean hasNext) {
    public ClubCommentPage{items=List.copyOf(items);}
}
