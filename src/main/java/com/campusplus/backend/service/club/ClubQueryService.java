package com.campusplus.backend.service.club;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.club.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
public class ClubQueryService {
    private final ClubWorkspace w;
    public ClubQueryService(ClubWorkspace w) { this.w=w; }
    private Pageable page(int page,int size) { return PageRequest.of(page,size,Sort.by("id")); }
    private Map<Long,Long> counts(List<Long> ids) {
        Map<Long,Long> map=new HashMap<>();
        if(!ids.isEmpty()) for(var c:w.memberships.counts(ids)) map.put(c.getClubId(),c.getAmount());
        return map;
    }
    public PageResponse<ClubSummary> list(@Positive Long universityId,@Size(max=200) String query,
                                         @Min(0) int page,@Min(1) @Max(100) int size) {
        var result=w.clubs.search(universityId,ClubStatus.ACTIVE,ClubWorkspace.search(query),page(page,size));
        var amounts=counts(result.getContent().stream().map(Club::getId).toList());
        return PageResponse.from(result.map(c -> ClubSummary.from(c,amounts.getOrDefault(c.getId(),0L))));
    }
    public ClubResponse get(@Positive Long id) {
        Club c=w.club(id); User user=w.actor();
        if(!w.policy.visible(c,user,w.local(c,user.getId()))) throw new ResourceNotFoundException("Kulüp bulunamadı.");
        return ClubResponse.from(c,w.count(c));
    }
    public ClubReviewResponse review(@Positive Long id) {
        Club c=w.club(id); User user=w.actor(); w.policy.requireOwner(user,w.local(c,user.getId()));
        return reviewDto(c);
    }
    private ClubReviewResponse reviewDto(Club c) {
        return new ClubReviewResponse(ClubResponse.from(c,w.count(c)),c.getReviewNote(),
                c.getReviewedBy()==null ? null : c.getReviewedBy().getId(),c.getReviewedAt());
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ClubReviewResponse> adminList(ClubStatus status,@Min(0) int page,@Min(1) @Max(100) int size) {
        // Queue is private; per-club counts are batched rather than one query per row.
        var result=w.clubs.search(null,status,"%",page(page,size));
        var amounts=counts(result.getContent().stream().map(Club::getId).toList());
        return PageResponse.from(result.map(c -> new ClubReviewResponse(ClubResponse.from(c,amounts.getOrDefault(c.getId(),0L)),
                c.getReviewNote(),c.getReviewedBy()==null ? null : c.getReviewedBy().getId(),c.getReviewedAt())));
    }
    public PageResponse<MyMembershipResponse> own(ClubMembershipStatus status,@Min(0) int page,@Min(1) @Max(100) int size) {
        var result=w.memberships.own(w.current.userId(),status,page(page,size));
        var amounts=counts(result.getContent().stream().map(m -> m.getClub().getId()).distinct().toList());
        return PageResponse.from(result.map(m -> MyMembershipResponse.from(m,amounts.getOrDefault(m.getClub().getId(),0L))));
    }
    public PageResponse<ManagedMembershipResponse> managed(@Positive Long clubId,ClubMembershipStatus status,
                                                          @Min(0) int page,@Min(1) @Max(100) int size) {
        w.preflightManagement(clubId,false);
        if(!w.policy.admin(w.actor())) w.policy.requireActive(w.club(clubId));
        return PageResponse.from(w.memberships.managed(clubId,status,page(page,size)));
    }
}
