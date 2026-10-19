package com.campusplus.backend.dto.club;

import com.campusplus.backend.entity.*;
import java.time.Instant;

public record ClubResponse(ClubSummary club, String description, ClubStatus status,
                           Instant createdAt, Instant updatedAt, Long version) {
    public static ClubResponse from(Club c, long count) {
        return new ClubResponse(ClubSummary.from(c, count), c.getDescription(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }
}
