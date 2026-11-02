package com.campusplus.backend.dto.comment;
import jakarta.validation.constraints.*;
public record UpdateCommentRequest(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=3000) String body) {
    public UpdateCommentRequest{body=body==null?null:body.strip();}
    @Override public String toString(){return "UpdateCommentRequest[content=[REDACTED]]";}
}
