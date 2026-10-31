package com.campusplus.backend.dto.event;
import com.campusplus.backend.entity.EventAudience;
import com.campusplus.backend.validation.HttpWebsite;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
public record CreateEventRequest(
    @NotBlank @Size(max=200) String title,@NotBlank @Size(max=10000) String description,
    @NotBlank @Size(max=500) String venue,@Size(max=500) @HttpWebsite String coverUrl,
    EventAudience audience,@Min(1) @Max(100000) Integer capacity,
    @NotNull OffsetDateTime registrationOpensAt,@NotNull OffsetDateTime registrationClosesAt,
    @NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,@NotBlank @Size(max=64) String timeZone) {
    public CreateEventRequest { title=EventInput.strip(title);description=EventInput.strip(description);venue=EventInput.strip(venue);
        coverUrl=EventInput.optional(coverUrl);timeZone=EventInput.strip(timeZone);
        audience=audience==null?EventAudience.UNIVERSITY_STUDENTS:audience; }
    @AssertTrue(message="Etkinlik tarihleri veya saat dilimi geçersiz") public boolean isScheduleValid(){
        return EventInput.schedule(registrationOpensAt,registrationClosesAt,startsAt,endsAt,timeZone);
    }
}
