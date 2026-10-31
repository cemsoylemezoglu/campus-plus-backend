package com.campusplus.backend.service.announcement;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.announcement.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
public class AnnouncementQueryService {
    private final AnnouncementWorkspace w;
    public AnnouncementQueryService(AnnouncementWorkspace w){this.w=w;}
    public PageResponse<AnnouncementSummary> discover(@Positive Long clubId,@Positive Long universityId,
            @Size(max=200) String query,@Min(0) int page,@Min(1) @Max(100) int size) {
        User actor=w.core.actor();
        return PageResponse.from(w.announcements.discover(clubId,universityId,ClubWorkspace.search(query),w.core.policy.admin(actor),actor.getId(),
            PageRequest.of(page,size,Sort.by("publishedAt").descending().and(Sort.by("id").descending()))).map(AnnouncementSummary::from));
    }
    public AnnouncementResponse get(@Positive Long id) {
        Announcement a=w.announcement(id);User actor=w.core.actor();ClubMembership local=w.core.local(a.getClub(),actor.getId());
        StudentProfile profile=w.core.profiles.findById(actor.getId()).orElse(null);
        w.policy.requireVisible(a,actor,local,profile);
        return new AnnouncementResponse(AnnouncementSummary.from(a),a.getBody(),w.link(a,actor,local));
    }
    public ManagedAnnouncementResponse managedDetail(@Positive Long clubId,@Positive Long id) {
        w.preflightManager(clubId);
        Announcement a=w.announcements.findByIdAndClubId(id,clubId)
            .orElseThrow(() -> new com.campusplus.backend.exception.ResourceNotFoundException("Duyuru bulunamadı."));
        return w.response(a,w.core.actor());
    }
    public PageResponse<ManagedAnnouncementSummary> managed(@Positive Long clubId,AnnouncementStatus status,Boolean archived,
            @Min(0) int page,@Min(1) @Max(100) int size) {
        w.preflightManager(clubId);
        return PageResponse.from(w.announcements.managed(clubId,status,archived,PageRequest.of(page,size,Sort.by("id").descending()))
            .map(ManagedAnnouncementSummary::from));
    }
}
