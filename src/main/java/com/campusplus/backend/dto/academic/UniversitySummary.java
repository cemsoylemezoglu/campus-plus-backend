package com.campusplus.backend.dto.academic;

import com.campusplus.backend.entity.University;

public record UniversitySummary(Long id, String code, String name, String city, String countryCode) {
    public static UniversitySummary from(University u) {
        return new UniversitySummary(u.getId(), u.getCode(), u.getName(), u.getCity(), u.getCountryCode());
    }
}
