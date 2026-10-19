package com.campusplus.backend.security;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import static com.campusplus.backend.entity.ClubMembershipRole.*;
import static com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE;

@Component
public class ClubPolicy {
    public boolean admin(User user) { return user.getRoles().contains(Role.ADMIN); }
    public boolean owner(User user, ClubMembership membership) {
        return user.getRoles().contains(Role.CLUB_ADMIN) && membership != null
                && membership.getUserId().equals(user.getId()) && membership.getStatus() == ACTIVE && membership.getRole() == OWNER;
    }
    public boolean manager(User user, ClubMembership membership) {
        return owner(user, membership) || (user.getRoles().contains(Role.CLUB_ADMIN) && membership != null
                && membership.getUserId().equals(user.getId()) && membership.getStatus() == ACTIVE && membership.getRole() == MANAGER);
    }
    public boolean visible(Club club, User user, ClubMembership membership) {
        if (admin(user) || club.getStatus() == ClubStatus.ACTIVE) return true;
        return switch (club.getStatus()) {
            case PENDING_APPROVAL, REJECTED -> owner(user, membership);
            case SUSPENDED -> membership != null && (membership.getStatus() == ACTIVE
                    || membership.getStatus() == ClubMembershipStatus.SUSPENDED);
            case ARCHIVED -> membership != null && membership.getJoinedAt() != null;
            default -> false;
        };
    }
    public void requireManager(User user, ClubMembership membership) {
        if (!admin(user) && !manager(user, membership)) deny();
    }
    public void requireOwner(User user, ClubMembership membership) {
        if (!admin(user) && !owner(user, membership)) deny();
    }
    public void requireAdmin(User user) { if (!admin(user)) deny(); }
    public void requireActive(Club club) {
        if (club.getStatus() != ClubStatus.ACTIVE) throw new ResourceConflictException("Kulüp aktif değil.");
    }
    public void deny() { throw new AccessDeniedException("Bu işlem için yetkiniz bulunmuyor."); }
}
