package com.campusplus.backend.dto.messaging;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReadDirectConversationRequest(@NotNull @Positive Long throughMessageNumber) {}
