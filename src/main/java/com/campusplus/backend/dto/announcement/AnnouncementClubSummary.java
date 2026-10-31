package com.campusplus.backend.dto.announcement;
import com.campusplus.backend.entity.Club;
public record AnnouncementClubSummary(Long id,String name,Long universityId,String universityName) {
    public static AnnouncementClubSummary from(Club c) {
        return new AnnouncementClubSummary(c.getId(),c.getName(),c.getUniversity().getId(),c.getUniversity().getName());
    }
    @Override public String toString(){return "AnnouncementClubSummary[id="+id+"]";}
}
