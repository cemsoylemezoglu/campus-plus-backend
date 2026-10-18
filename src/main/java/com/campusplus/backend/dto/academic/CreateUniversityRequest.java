package com.campusplus.backend.dto.academic;

import com.campusplus.backend.validation.HttpWebsite;
import jakarta.validation.constraints.*;
import java.util.*;

public record CreateUniversityRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @Size(max = 500) @HttpWebsite String websiteUrl
) {
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
    public CreateUniversityRequest {
        code = AcademicText.upper(code); name = AcademicText.strip(name);
        city = AcademicText.strip(city); countryCode = AcademicText.upper(countryCode);
        websiteUrl = AcademicText.optional(websiteUrl);
    }
    @AssertTrue(message = "Geçerli bir ISO ülke kodu giriniz")
    public boolean isCountrySupported() { return countryCode == null || COUNTRIES.contains(countryCode); }
}
