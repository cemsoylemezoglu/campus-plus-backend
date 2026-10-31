package com.campusplus.backend.dto.event;
import jakarta.validation.constraints.*;
public record RegisterEventRequest(@NotNull @PositiveOrZero Long eventVersion,@PositiveOrZero Long participationVersion) {}
