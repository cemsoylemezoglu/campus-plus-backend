package com.campusplus.backend.repository;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.dto.club.ManagedMembershipResponse;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, Long> {
    Optional<ClubMembership> findByClubIdAndUserId(Long clubId, Long userId);
    Optional<ClubMembership> findByIdAndClubId(Long id, Long clubId);
    long countByClubIdAndStatus(Long clubId, ClubMembershipStatus status);
    boolean existsByUserIdAndStatusIn(Long userId, Collection<ClubMembershipStatus> statuses);
    @Query("select m.userId from ClubMembership m where m.club.id=:clubId and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE "
            + "and m.role<>com.campusplus.backend.entity.ClubMembershipRole.MEMBER order by m.userId")
    List<Long> activeManagerUserIds(@Param("clubId") Long clubId);
    @Query("select m from ClubMembership m where m.club.id=:clubId and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE "
            + "and m.role=com.campusplus.backend.entity.ClubMembershipRole.OWNER")
    Optional<ClubMembership> activeOwner(@Param("clubId") Long clubId);
    @Query("select count(m)>0 from ClubMembership m where m.userId=:userId and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE "
            + "and m.role<>com.campusplus.backend.entity.ClubMembershipRole.MEMBER and m.club.status<>com.campusplus.backend.entity.ClubStatus.ARCHIVED")
    boolean hasManagement(@Param("userId") Long userId);
    interface MemberCount { Long getClubId(); long getAmount(); }
    @Query("select m.club.id as clubId, count(m) as amount from ClubMembership m where m.club.id in :ids "
            + "and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE group by m.club.id")
    List<MemberCount> counts(@Param("ids") Collection<Long> ids);
    @EntityGraph(attributePaths={"club", "club.university"})
    @Query("select m from ClubMembership m where m.userId=:userId and (:status is null or m.status=:status)")
    Page<ClubMembership> own(@Param("userId") Long userId, @Param("status") ClubMembershipStatus status, Pageable pageable);
    @Query("select new com.campusplus.backend.dto.club.ManagedMembershipResponse(m.id,m.userId,u.name,m.role,m.status,m.requestedAt,m.joinedAt,m.endedAt,m.version) "
            + "from ClubMembership m join m.studentProfile p join p.user u where m.club.id=:clubId and (:status is null or m.status=:status)")
    Page<ManagedMembershipResponse> managed(@Param("clubId") Long clubId, @Param("status") ClubMembershipStatus status, Pageable pageable);
}
