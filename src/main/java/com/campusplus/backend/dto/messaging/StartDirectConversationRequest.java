package com.campusplus.backend.dto.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StartDirectConversationRequest(@NotNull @Positive Long recipientUserId,
        @NotNull @Valid SendDirectMessageRequest message) {
    @Override public String toString() {
        return "StartDirectConversationRequest[content=[REDACTED]]";
    }
}
