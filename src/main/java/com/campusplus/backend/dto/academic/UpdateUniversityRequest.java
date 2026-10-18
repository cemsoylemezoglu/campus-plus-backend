package com.campusplus.backend.dto.academic;

import com.campusplus.backend.validation.HttpWebsite;
import jakarta.validation.constraints.*;

public record UpdateUniversityRequest(
        @Size(min = 1, max = 200) String name,
        @Size(min = 1, max = 100) String city,
        @Size(max = 500) @HttpWebsite String websiteUrl,
        Boolean active,
        @NotNull @PositiveOrZero Long version
) {
    public UpdateUniversityRequest {
        name = AcademicText.strip(name); city = AcademicText.strip(city);
        // null means unchanged; an explicitly empty string clears the website.
        websiteUrl = AcademicText.strip(websiteUrl);
    }
    @AssertTrue(message = "En az bir değişiklik alanı gönderiniz")
    public boolean isChangePresent() { return name != null || city != null || websiteUrl != null || active != null; }
}
