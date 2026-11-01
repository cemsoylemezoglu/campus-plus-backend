package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.PostAudience;
import jakarta.validation.constraints.*;

public record UpdatePostRequest(@NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 10000) String body, PostAudience audience) {
    public UpdatePostRequest { body = body == null ? null : body.strip(); }
    @Override public String toString() { return "UpdatePostRequest[content=[REDACTED]]"; }
}
