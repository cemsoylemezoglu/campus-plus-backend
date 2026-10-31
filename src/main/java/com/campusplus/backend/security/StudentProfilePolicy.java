package com.campusplus.backend.security;

import com.campusplus.backend.entity.ClubMembershipStatus;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.repository.ClubMembershipRepository;
import com.campusplus.backend.repository.EventParticipationRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudentProfilePolicy {
    private final ClubMembershipRepository memberships;
    private final EventParticipationRepository participations;
    private final Clock clock;
    public StudentProfilePolicy(ClubMembershipRepository memberships,EventParticipationRepository participations,Clock clock) {
        this.memberships=memberships;this.participations=participations;this.clock=clock;
    }
    public void requireUniversityChangeAllowed(Long userId) {
        if(memberships.existsByUserIdAndStatusIn(userId,List.of(ClubMembershipStatus.PENDING,ClubMembershipStatus.ACTIVE,ClubMembershipStatus.SUSPENDED)))
            throw new ResourceConflictException("Üniversite değişikliği için aktif/bekleyen/askıdaki üyeliklerinizi sonlandırınız.");
        if(participations.blocksUniversityChange(userId,clock.instant()))
            throw new ResourceConflictException("Üniversite değişikliği için devam eden etkinlik kayıtlarınızı sonlandırınız.");
    }
}
