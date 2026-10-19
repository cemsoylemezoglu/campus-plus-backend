package com.campusplus.backend.dto.club;

import jakarta.validation.constraints.*;
import com.campusplus.backend.validation.HttpWebsite;
import com.campusplus.backend.entity.*;
import java.util.Locale;

public record TransferOwnershipRequest(@NotNull @Positive Long targetMembershipId, @NotNull @PositiveOrZero Long clubVersion, @NotNull @PositiveOrZero Long ownerMembershipVersion, @NotNull @PositiveOrZero Long targetMembershipVersion) {}
