package com.campusplus.backend.security;

import com.campusplus.backend.entity.ClubMembershipStatus;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.repository.ClubMembershipRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudentProfilePolicy {
    private final ClubMembershipRepository memberships;
    public StudentProfilePolicy(ClubMembershipRepository memberships) { this.memberships=memberships; }
    public void requireUniversityChangeAllowed(Long userId) {
        if(memberships.existsByUserIdAndStatusIn(userId,List.of(ClubMembershipStatus.PENDING,ClubMembershipStatus.ACTIVE,ClubMembershipStatus.SUSPENDED)))
            throw new ResourceConflictException("Üniversite değişikliği için aktif/bekleyen/askıdaki üyeliklerinizi sonlandırınız.");
    }
}
