package com.campusplus.backend.dto.notification;
import jakarta.validation.constraints.*;
public record ReadAllNotificationsRequest(@NotNull @Positive Long upToId) {}
