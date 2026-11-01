package com.campusplus.backend.security;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class PostPolicy {
    private final ClubPolicy clubs;
    public PostPolicy(ClubPolicy clubs) { this.clubs = clubs; }

    public void requireOwn(Post post, User actor) {
        if (post.getPublisher() != PostPublisher.STUDENT || !post.getCreatedBy().getId().equals(actor.getId())) missing();
    }
    public void requireManager(Club club, User actor, ClubMembership membership) {
        if (!clubs.admin(actor) && !(membership != null && membership.getClub().getId().equals(club.getId()) && clubs.manager(actor, membership)))
            throw new AccessDeniedException("Bu işlem için yetkiniz bulunmuyor.");
    }
    public void requireStudent(User actor) {
        if (!actor.getRoles().contains(Role.STUDENT)) throw new AccessDeniedException("Öğrenci rolü gereklidir.");
    }
    public void requireEditable(Post post) {
        if (post.getStatus() == PostStatus.REMOVED || post.isModerationHidden())
            throw new ResourceConflictException("Gönderi düzenlemeye kapalı.");
    }
    public void requireSameUniversity(Post post, StudentProfile profile) {
        if (!post.getUniversity().getId().equals(profile.getUniversity().getId()))
            throw new ResourceConflictException("Gönderi eski üniversitenize ait; yalnız kaldırılabilir.");
    }
    public void requireAudience(Post post, PostAudience audience) {
        if (post.getPublisher() == PostPublisher.STUDENT && audience != PostAudience.UNIVERSITY_STUDENTS)
            throw new InvalidRequestException("Öğrenci gönderisi üniversite kitlesinde olmalı.");
        if (post.getStatus() != PostStatus.DRAFT && audience != post.getAudience())
            throw new ResourceConflictException("Yayımlanmış gönderinin kitlesi değiştirilemez.");
    }
    public boolean visible(Post p, User actor, StudentProfile profile, ClubMembership membership) {
        return profile != null && profile.getUserId().equals(actor.getId()) && p.getUniversity().isActive()
                && profile.getUniversity().getId().equals(p.getUniversity().getId())
                && p.getStatus() == PostStatus.PUBLISHED && !p.isModerationHidden()
                && (p.getClub() == null || p.getClub().getStatus() == ClubStatus.ACTIVE)
                && (p.getAudience() == PostAudience.UNIVERSITY_STUDENTS || membership != null
                    && membership.getUserId().equals(actor.getId()) && p.getClub() != null
                    && membership.getClub().getId().equals(p.getClub().getId()) && membership.getStatus() == ClubMembershipStatus.ACTIVE);
    }
    public void missing() { throw new ResourceNotFoundException("Gönderi bulunamadı."); }
}
