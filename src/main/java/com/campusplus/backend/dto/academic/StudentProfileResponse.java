package com.campusplus.backend.dto.academic;

import com.campusplus.backend.entity.StudentProfile;
import java.time.Instant;

public record StudentProfileResponse(Long userId, UniversitySummary university, String department,
                                     Short studyYear, String bio, Instant createdAt, Instant updatedAt, Long version) {
    public static StudentProfileResponse from(StudentProfile p) {
        return new StudentProfileResponse(p.getUserId(), UniversitySummary.from(p.getUniversity()),
                p.getDepartment(), p.getStudyYear(), p.getBio(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }
}
