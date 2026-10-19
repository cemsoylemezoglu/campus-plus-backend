package com.campusplus.backend.service.club;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import jakarta.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

// Called inside transactional service methods only. All writers use users ASC -> club -> memberships.
@Component
public class ClubWorkspace {
    public final ClubRepository clubs;
    public final ClubMembershipRepository memberships;
    public final StudentProfileRepository profiles;
    public final UniversityRepository universities;
    public final UserRepository users;
    public final CurrentActor current;
    public final ClubPolicy policy;
    public final ClubMembershipPolicy membershipPolicy;
    public final Clock clock;
    private final EntityManager entityManager;
    public ClubWorkspace(ClubRepository clubs, ClubMembershipRepository memberships, StudentProfileRepository profiles,
            UniversityRepository universities, UserRepository users, CurrentActor current, ClubPolicy policy,
            ClubMembershipPolicy membershipPolicy, Clock clock, EntityManager entityManager) {
        this.clubs=clubs; this.memberships=memberships; this.profiles=profiles; this.universities=universities;
        this.users=users; this.current=current; this.policy=policy; this.membershipPolicy=membershipPolicy;
        this.clock=clock; this.entityManager=entityManager;
    }
    public record Locked(Club club, User actor, Map<Long,User> users) {}
    public User actor() { return users.findById(current.userId()).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı.")); }
    public Club club(Long id) { return clubs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Kulüp bulunamadı.")); }
    public ClubMembership local(Club club, Long userId) {
        ClubMembership m=memberships.findByClubIdAndUserId(club.getId(), userId).orElse(null);
        if(m!=null) entityManager.refresh(m);
        return m;
    }
    public User lockUser(Long id) {
        User user=users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
        // A lock alone need not refresh an already-managed entity (e.g. OSIV after JWT conversion).
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
    public Locked lock(Long clubId, Collection<Long> affected) {
        SortedSet<Long> ids=new TreeSet<>(affected); ids.add(current.userId());
        Map<Long,User> locked=new LinkedHashMap<>();
        for(Long id:ids) locked.put(id,lockUser(id));
        Club club=clubs.findByIdForUpdate(clubId).orElseThrow(() -> new ResourceNotFoundException("Kulüp bulunamadı."));
        entityManager.refresh(club,LockModeType.PESSIMISTIC_WRITE);
        return new Locked(club,locked.get(current.userId()),locked);
    }
    public ClubMembership member(Long clubId, Long memberId) {
        ClubMembership m=memberships.findByIdAndClubId(memberId,clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Üyelik bulunamadı."));
        entityManager.refresh(m);
        return m;
    }
    public ClubMembership self(Club club) {
        ClubMembership m=local(club,current.userId());
        if(m==null) throw new ResourceNotFoundException("Üyelik bulunamadı.");
        entityManager.refresh(m); return m;
    }
    public StudentProfile profile(Long userId) {
        StudentProfile profile=profiles.findById(userId).orElseThrow(() -> new ResourceConflictException("Öğrenci profilinizi tamamlayınız."));
        entityManager.refresh(profile); return profile;
    }
    public StudentProfile eligible(Long userId,Club club) {
        StudentProfile profile=profile(userId);
        University university=universities.findByIdForShare(club.getUniversity().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        entityManager.refresh(university,LockModeType.PESSIMISTIC_READ);
        membershipPolicy.sameUniversity(profile,club); return profile;
    }
    public void refreshUniversity(University university) { entityManager.refresh(university,LockModeType.PESSIMISTIC_READ); }
    public void preflightManagement(Long clubId, boolean owner) {
        Club c=club(clubId); User user=actor(); ClubMembership local=local(c,user.getId());
        if(owner) policy.requireOwner(user,local); else policy.requireManager(user,local);
    }
    public void syncRole(User user) {
        memberships.flush();
        boolean manages=memberships.hasManagement(user.getId());
        boolean had=user.getRoles().contains(Role.CLUB_ADMIN);
        if(manages) user.grantRole(Role.CLUB_ADMIN); else user.revokeRole(Role.CLUB_ADMIN);
        if(had!=manages) users.save(user);
    }
    public long count(Club club) { return memberships.countByClubIdAndStatus(club.getId(),ClubMembershipStatus.ACTIVE); }
    public static void version(Long actual, Long expected) {
        if(expected==null || !Objects.equals(actual,expected)) throw new ResourceConflictException("Kayıt değişti; güncel sürümü alınız.");
    }
    public static String reason(String value) {
        if(value==null || value.isBlank()) throw new InvalidRequestException("İşlem gerekçesi zorunludur.");
        return value.strip();
    }
    public static String search(String value) {
        String term=value==null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return "%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
}
