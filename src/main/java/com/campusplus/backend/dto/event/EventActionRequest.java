package com.campusplus.backend.dto.event;
import jakarta.validation.constraints.*;
public record EventActionRequest(@NotNull @PositiveOrZero Long version) {}
