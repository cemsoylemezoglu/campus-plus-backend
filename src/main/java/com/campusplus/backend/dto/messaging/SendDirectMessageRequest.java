package com.campusplus.backend.dto.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record SendDirectMessageRequest(@NotNull UUID clientMessageId,
        @NotBlank @Size(max = 4000) @Pattern(regexp = "[^\\x00]*", message = "Geçersiz metin.") String body) {
    public SendDirectMessageRequest {
        body = body == null ? null : body.strip();
    }

    @Override public String toString() {
        return "SendDirectMessageRequest[content=[REDACTED]]";
    }
}
