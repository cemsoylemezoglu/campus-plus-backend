package com.campusplus.backend.dto.club;

import com.campusplus.backend.entity.*;
import java.time.Instant;

public record MyMembershipResponse(Long id, ClubSummary club, ClubMembershipRole role, ClubMembershipStatus status,
        Instant requestedAt, Instant joinedAt, Instant endedAt, String decisionReason, Long version) {
    public static MyMembershipResponse from(ClubMembership m, long count) {
        return new MyMembershipResponse(m.getId(), ClubSummary.from(m.getClub(), count), m.getRole(), m.getStatus(),
                m.getRequestedAt(), m.getJoinedAt(), m.getEndedAt(), m.getDecisionReason(), m.getVersion());
    }
}
