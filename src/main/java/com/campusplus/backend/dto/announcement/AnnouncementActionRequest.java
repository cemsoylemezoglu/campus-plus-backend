package com.campusplus.backend.dto.announcement;
import jakarta.validation.constraints.*;
public record AnnouncementActionRequest(@NotNull @PositiveOrZero Long version) {}
