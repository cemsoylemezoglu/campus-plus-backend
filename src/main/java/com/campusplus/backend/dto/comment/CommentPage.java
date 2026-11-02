package com.campusplus.backend.dto.comment;
import java.util.List;
public record CommentPage(List<CommentResponse> items,String nextCursor,boolean hasNext) {
    public CommentPage{items=List.copyOf(items);}
}
