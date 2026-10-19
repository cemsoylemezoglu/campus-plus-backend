package com.campusplus.backend.dto.club;

import jakarta.validation.constraints.*;
import com.campusplus.backend.validation.HttpWebsite;
import com.campusplus.backend.entity.*;
import java.util.Locale;

public record MembershipDecisionRequest(@NotNull @PositiveOrZero Long version, @Size(max=1000) String reason) {}
