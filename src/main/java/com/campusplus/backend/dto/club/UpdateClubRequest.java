package com.campusplus.backend.dto.club;

import jakarta.validation.constraints.*;
import com.campusplus.backend.validation.HttpWebsite;
import com.campusplus.backend.entity.*;
import java.util.Locale;

public record UpdateClubRequest(@Size(min=1,max=150) String name,
        @Size(min=1,max=5000) String description, @Size(max=500) @HttpWebsite String logoUrl,
        @NotNull @PositiveOrZero Long version) {
    public UpdateClubRequest {
        name = name == null ? null : name.strip(); description = description == null ? null : description.strip();
        logoUrl = logoUrl == null ? null : logoUrl.strip();
    }
    @AssertTrue(message="En az bir değişiklik alanı gönderiniz")
    public boolean isChangePresent() { return name != null || description != null || logoUrl != null; }
}
