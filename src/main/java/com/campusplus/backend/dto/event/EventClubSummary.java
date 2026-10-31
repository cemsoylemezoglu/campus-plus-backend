package com.campusplus.backend.dto.event;
import com.campusplus.backend.entity.Club;
public record EventClubSummary(Long id,String name,Long universityId,String universityName) {
    public static EventClubSummary from(Club c){return new EventClubSummary(c.getId(),c.getName(),c.getUniversity().getId(),c.getUniversity().getName());}
}
