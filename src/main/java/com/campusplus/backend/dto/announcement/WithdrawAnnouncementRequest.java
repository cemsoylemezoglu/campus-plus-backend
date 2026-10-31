package com.campusplus.backend.dto.announcement;
import jakarta.validation.constraints.*;
public record WithdrawAnnouncementRequest(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=1000) String reason) {
    public WithdrawAnnouncementRequest {reason=reason==null?null:reason.strip();}
    @Override public String toString(){return "WithdrawAnnouncementRequest[version="+version+", reason=[REDACTED]]";}
}
