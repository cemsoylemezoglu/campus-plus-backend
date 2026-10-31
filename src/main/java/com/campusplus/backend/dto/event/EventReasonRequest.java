package com.campusplus.backend.dto.event;
import jakarta.validation.constraints.*;
public record EventReasonRequest(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=1000) String reason) {
    public EventReasonRequest {reason=EventInput.strip(reason);}
}
