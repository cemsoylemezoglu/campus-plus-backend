package com.campusplus.backend.dto.club;

import jakarta.validation.constraints.*;
import com.campusplus.backend.validation.HttpWebsite;
import com.campusplus.backend.entity.*;
import java.util.Locale;

public record CreateClubRequest(@NotBlank @Size(max=150) String name,
        @NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @NotBlank @Size(max=5000) String description, @Size(max=500) @HttpWebsite String logoUrl) {
    public CreateClubRequest {
        name = name == null ? null : name.strip(); description = description == null ? null : description.strip();
        slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-");
        logoUrl = logoUrl == null || logoUrl.isBlank() ? null : logoUrl.strip();
    }
}
