package com.campusplus.backend.dto.club;

import jakarta.validation.constraints.*;
import com.campusplus.backend.validation.HttpWebsite;
import com.campusplus.backend.entity.*;
import java.util.Locale;

public record ChangeMembershipRoleRequest(@NotNull ClubMembershipRole role, @NotNull @PositiveOrZero Long version) {
    @AssertTrue(message="Sahiplik için ayrı devir endpointini kullanınız")
    public boolean isAssignableRole() { return role == null || role != ClubMembershipRole.OWNER; }
}
