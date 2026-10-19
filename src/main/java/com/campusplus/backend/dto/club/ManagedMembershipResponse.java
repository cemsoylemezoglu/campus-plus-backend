package com.campusplus.backend.dto.club;

import com.campusplus.backend.entity.*;
import java.time.Instant;
public record ManagedMembershipResponse(Long id, Long userId, String displayName, ClubMembershipRole role,
        ClubMembershipStatus status, Instant requestedAt, Instant joinedAt, Instant endedAt, Long version) {}
