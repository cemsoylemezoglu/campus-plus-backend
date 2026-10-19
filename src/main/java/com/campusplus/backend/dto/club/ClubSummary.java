package com.campusplus.backend.dto.club;

import com.campusplus.backend.dto.academic.UniversitySummary;
import com.campusplus.backend.entity.Club;

public record ClubSummary(Long id, UniversitySummary university, String name, String slug, String logoUrl, long activeMemberCount) {
    public static ClubSummary from(Club c, long count) {
        return new ClubSummary(c.getId(), UniversitySummary.from(c.getUniversity()), c.getName(), c.getSlug(), c.getLogoUrl(), count);
    }
}
