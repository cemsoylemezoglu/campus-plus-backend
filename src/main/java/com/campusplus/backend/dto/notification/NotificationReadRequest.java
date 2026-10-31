package com.campusplus.backend.dto.notification;
import jakarta.validation.constraints.*;
public record NotificationReadRequest(@NotNull @PositiveOrZero Long version) {}
