package com.campusplus.backend.dto.event;
import com.campusplus.backend.validation.HttpWebsite;
import com.campusplus.backend.entity.EventAudience;
import jakarta.validation.constraints.*;
public record UpdateEventRequest(@Size(min=1,max=200) String title,@Size(min=1,max=10000) String description,
    @Size(min=1,max=500) String venue,@Size(max=500) @HttpWebsite String coverUrl,
    EventAudience audience,@Min(1) @Max(100000) Integer capacity,Boolean unlimited,@NotNull @PositiveOrZero Long version) {
    public UpdateEventRequest { title=EventInput.strip(title);description=EventInput.strip(description);venue=EventInput.strip(venue);coverUrl=EventInput.strip(coverUrl); }
    @AssertTrue(message="Boş veya çelişen güncelleme") public boolean isPatchValid(){
        return (title!=null||description!=null||venue!=null||coverUrl!=null||audience!=null||capacity!=null||unlimited!=null)
            && (title==null||!title.isBlank()) && (description==null||!description.isBlank()) && (venue==null||!venue.isBlank())
            && (!Boolean.TRUE.equals(unlimited)||capacity==null) && (!Boolean.FALSE.equals(unlimited)||capacity!=null);
    }
}
