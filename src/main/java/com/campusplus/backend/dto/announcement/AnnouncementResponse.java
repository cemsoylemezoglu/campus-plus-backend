package com.campusplus.backend.dto.announcement;
public record AnnouncementResponse(AnnouncementSummary announcement,String body,LinkedAnnouncementEvent event) {
    @Override public String toString(){return "AnnouncementResponse[content=[REDACTED]]";}
}
