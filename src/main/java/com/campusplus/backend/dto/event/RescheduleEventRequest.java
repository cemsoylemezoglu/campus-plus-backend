package com.campusplus.backend.dto.event;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
public record RescheduleEventRequest(@NotNull @PositiveOrZero Long version,
    @NotNull OffsetDateTime registrationOpensAt,@NotNull OffsetDateTime registrationClosesAt,
    @NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,@NotBlank @Size(max=64) String timeZone,
    @Size(max=1000) String reason) {
    public RescheduleEventRequest {timeZone=EventInput.strip(timeZone);reason=EventInput.optional(reason);}
    @AssertTrue(message="Etkinlik tarihleri veya saat dilimi geçersiz") public boolean isScheduleValid(){
        return EventInput.schedule(registrationOpensAt,registrationClosesAt,startsAt,endsAt,timeZone);
    }
}
