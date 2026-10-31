package com.campusplus.backend.dto.announcement;

import com.campusplus.backend.entity.AnnouncementAudience;
import jakarta.validation.constraints.*;

public record CreateAnnouncementRequest(@NotBlank @Size(max=200) String title,
        @NotBlank @Size(max=10000) String body, AnnouncementAudience audience, @Positive Long eventId) {
    public CreateAnnouncementRequest {
        title=title==null?null:title.strip();body=body==null?null:body.strip();
        audience=audience==null?AnnouncementAudience.UNIVERSITY_STUDENTS:audience;
    }
    @Override public String toString(){return "CreateAnnouncementRequest[content=[REDACTED]]";}
}
