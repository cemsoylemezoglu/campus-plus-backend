package com.campusplus.backend.dto.comment;
public record AdminCommentResponse(OwnCommentResponse comment,Long authorId) {
    public static AdminCommentResponse from(CommentRow c){return new AdminCommentResponse(OwnCommentResponse.from(c),c.authorId());}
    @Override public String toString(){return "AdminCommentResponse[content=[REDACTED]]";}
}
