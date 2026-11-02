package com.campusplus.backend.dto.comment;
import jakarta.validation.constraints.*;
public record CreateCommentRequest(@NotBlank @Size(max=3000) String body) {
    public CreateCommentRequest{body=body==null?null:body.strip();}
    @Override public String toString(){return "CreateCommentRequest[content=[REDACTED]]";}
}
