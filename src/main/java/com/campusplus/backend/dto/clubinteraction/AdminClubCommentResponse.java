package com.campusplus.backend.dto.clubinteraction;
public record AdminClubCommentResponse(OwnClubCommentResponse comment,Long authorId) {
    public static AdminClubCommentResponse from(ClubCommentRow c){return new AdminClubCommentResponse(OwnClubCommentResponse.from(c),c.authorId());}
    @Override public String toString(){return "AdminClubCommentResponse[content=[REDACTED]]";}
}
