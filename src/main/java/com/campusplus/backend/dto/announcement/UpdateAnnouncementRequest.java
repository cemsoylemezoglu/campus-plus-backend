package com.campusplus.backend.dto.announcement;

import com.campusplus.backend.entity.AnnouncementAudience;
import jakarta.validation.constraints.*;

public record UpdateAnnouncementRequest(@NotNull @PositiveOrZero Long version,
        @Size(min=1,max=200) String title,@Size(min=1,max=10000) String body,
        AnnouncementAudience audience,@Positive Long eventId,Boolean clearEvent) {
    public UpdateAnnouncementRequest { title=title==null?null:title.strip();body=body==null?null:body.strip(); }
    @AssertTrue(message="Boş veya çelişen duyuru güncellemesi")
    public boolean isPatchValid() {
        return (title!=null||body!=null||audience!=null||eventId!=null||Boolean.TRUE.equals(clearEvent))
            && (title==null||!title.isBlank()) && (body==null||!body.isBlank())
            && !(Boolean.TRUE.equals(clearEvent)&&eventId!=null);
    }
    @Override public String toString(){return "UpdateAnnouncementRequest[version="+version+", content=[REDACTED]]";}
}
