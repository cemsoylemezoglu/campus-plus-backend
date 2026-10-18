package com.campusplus.backend.dto.academic;

import com.campusplus.backend.entity.University;
import java.time.Instant;

public record UniversityResponse(Long id, String code, String name, String city, String countryCode,
                                 String websiteUrl, boolean active, Instant createdAt, Instant updatedAt, Long version) {
    public static UniversityResponse from(University u) {
        return new UniversityResponse(u.getId(), u.getCode(), u.getName(), u.getCity(), u.getCountryCode(),
                u.getWebsiteUrl(), u.isActive(), u.getCreatedAt(), u.getUpdatedAt(), u.getVersion());
    }
}
