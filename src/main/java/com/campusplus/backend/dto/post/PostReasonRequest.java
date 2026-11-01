package com.campusplus.backend.dto.post;

import jakarta.validation.constraints.*;

public record PostReasonRequest(@NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 1000) String reason) {
    public PostReasonRequest { reason = reason == null ? null : reason.strip(); }
    @Override public String toString() { return "PostReasonRequest[reason=[REDACTED]]"; }
}
