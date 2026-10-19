package com.campusplus.backend.security;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import org.springframework.stereotype.Component;

@Component
public class ClubMembershipPolicy {
    public void sameUniversity(StudentProfile profile, Club club) {
        if (!profile.getUniversity().getId().equals(club.getUniversity().getId()))
            throw new ResourceConflictException("Yalnızca kendi üniversitenizin kulüplerine katılabilirsiniz.");
        if (!club.getUniversity().isActive()) throw new ResourceConflictException("Üniversite aktif değil.");
    }
    public void protectOwner(Club club, ClubMembership member) {
        if (member.getRole() == ClubMembershipRole.OWNER && club.getStatus() != ClubStatus.ARCHIVED)
            throw new ResourceConflictException("Son sahip önce sahipliği devretmeli veya kulübü arşivlemelidir.");
    }
}
