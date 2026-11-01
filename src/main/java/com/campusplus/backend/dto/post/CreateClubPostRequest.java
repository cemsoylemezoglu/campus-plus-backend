package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.PostAudience;
import jakarta.validation.constraints.*;

public record CreateClubPostRequest(@NotBlank @Size(max = 10000) String body, PostAudience audience) {
    public CreateClubPostRequest {
        body = body == null ? null : body.strip();
        audience = audience == null ? PostAudience.UNIVERSITY_STUDENTS : audience;
    }
    @Override public String toString() { return "CreateClubPostRequest[content=[REDACTED]]"; }
}
