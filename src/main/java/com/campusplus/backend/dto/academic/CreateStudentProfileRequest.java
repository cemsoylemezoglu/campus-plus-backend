package com.campusplus.backend.dto.academic;

import jakarta.validation.constraints.*;

public record CreateStudentProfileRequest(
        @NotNull @Positive Long universityId,
        @NotBlank @Size(max = 150) String department,
        @Min(0) @Max(8) Short studyYear,
        @Size(max = 1000) String bio
) {
    public CreateStudentProfileRequest {
        department = AcademicText.strip(department); bio = AcademicText.optional(bio);
    }
}
