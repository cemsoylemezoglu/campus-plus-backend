package com.campusplus.backend.dto.post;

import jakarta.validation.constraints.*;

public record PostActionRequest(@NotNull @PositiveOrZero Long version) {}
