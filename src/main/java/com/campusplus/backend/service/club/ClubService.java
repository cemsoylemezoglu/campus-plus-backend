package com.campusplus.backend.service.club;

import com.campusplus.backend.dto.club.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class ClubService {
    private final ClubWorkspace w;
    public ClubService(ClubWorkspace w) { this.w=w; }
    public ClubResponse create(@Valid CreateClubRequest request) {
        User creator=w.lockUser(w.current.userId());
        if(!creator.getRoles().contains(Role.STUDENT)) w.policy.deny();
        StudentProfile profile=w.profile(creator.getId());
        University university=w.universities.findByIdForShare(profile.getUniversity().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        w.refreshUniversity(university);
        if(!university.isActive()) throw new ResourceConflictException("Üniversite aktif değil.");
        if(w.clubs.existsByUniversityIdAndSlug(university.getId(),request.slug())) throw new ResourceConflictException("Kulüp adresi zaten kullanılıyor.");
        Club club=w.clubs.saveAndFlush(new Club(university,creator,request.name(),request.slug(),request.description(),request.logoUrl(),w.clock.instant()));
        w.memberships.saveAndFlush(new ClubMembership(club,profile,true,w.clock.instant()));
        w.syncRole(creator);
        return ClubResponse.from(club,w.count(club));
    }
    public ClubResponse update(@Positive Long id,@Valid UpdateClubRequest request) {
        w.preflightManagement(id,false);
        var ctx=w.lock(id,List.of()); Club c=ctx.club(); var local=w.local(c,ctx.actor().getId());
        if(c.getStatus()==ClubStatus.ACTIVE) w.policy.requireManager(ctx.actor(),local);
        else if(c.getStatus()==ClubStatus.PENDING_APPROVAL || c.getStatus()==ClubStatus.REJECTED) w.policy.requireOwner(ctx.actor(),local);
        else throw new ResourceConflictException("Kulüp düzenlemeye kapalı.");
        ClubWorkspace.version(c.getVersion(),request.version());
        c.edit(request.name()==null?c.getName():request.name(),request.description()==null?c.getDescription():request.description(),
                request.logoUrl()==null?c.getLogoUrl():(request.logoUrl().isBlank()?null:request.logoUrl()),w.clock.instant());
        return response(w.clubs.saveAndFlush(c));
    }
    public ClubResponse resubmit(@Positive Long id,@Valid ClubActionRequest request) {
        w.preflightManagement(id,true); var ctx=w.lock(id,List.of()); Club c=ctx.club();
        w.policy.requireOwner(ctx.actor(),w.local(c,ctx.actor().getId()));
        return transition(c,ctx.actor(),request,ClubStatus.REJECTED,ClubStatus.PENDING_APPROVAL,false);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public ClubResponse review(@Positive Long id,@Valid ClubActionRequest request,ReviewAction action) {
        var ctx=w.lock(id,List.of()); w.policy.requireAdmin(ctx.actor());
        return switch(action) {
            case APPROVE -> transition(ctx.club(),ctx.actor(),request,ClubStatus.PENDING_APPROVAL,ClubStatus.ACTIVE,false);
            case REJECT -> transition(ctx.club(),ctx.actor(),request,ClubStatus.PENDING_APPROVAL,ClubStatus.REJECTED,true);
            case SUSPEND -> transition(ctx.club(),ctx.actor(),request,ClubStatus.ACTIVE,ClubStatus.SUSPENDED,true);
            case RESTORE -> transition(ctx.club(),ctx.actor(),request,ClubStatus.SUSPENDED,ClubStatus.ACTIVE,false);
        };
    }
    public enum ReviewAction { APPROVE, REJECT, SUSPEND, RESTORE }
    private ClubResponse transition(Club c,User actor,ClubActionRequest request,ClubStatus from,ClubStatus to,boolean reasonRequired) {
        ClubWorkspace.version(c.getVersion(),request.version());
        if(c.getStatus()!=from) throw new ResourceConflictException("Bu durum geçişi yapılamaz.");
        if(w.memberships.activeOwner(c.getId()).isEmpty()) throw new ResourceConflictException("Aktif kulüp sahibi olmadan geçiş yapılamaz.");
        String note=reasonRequired?ClubWorkspace.reason(request.reason()):request.reason();
        c.transition(to,actor,note,w.clock.instant()); return response(w.clubs.saveAndFlush(c));
    }
    public ClubResponse archive(@Positive Long id,@Valid ClubActionRequest request) {
        w.preflightManagement(id,true);
        var ids=new TreeSet<>(w.memberships.activeManagerUserIds(id));
        var ctx=w.lock(id,ids); Club c=ctx.club();
        w.policy.requireOwner(ctx.actor(),w.local(c,ctx.actor().getId()));
        ClubWorkspace.version(c.getVersion(),request.version());
        if(c.getStatus()==ClubStatus.ARCHIVED) throw new ResourceConflictException("Kulüp zaten arşivlenmiş.");
        if(!ids.equals(new TreeSet<>(w.memberships.activeManagerUserIds(id)))) throw new ResourceConflictException("Yönetici listesi değişti; tekrar deneyiniz.");
        c.transition(ClubStatus.ARCHIVED,ctx.actor(),request.reason(),w.clock.instant()); w.clubs.saveAndFlush(c);
        for(Long userId:ids) w.syncRole(ctx.users().get(userId));
        return response(c);
    }
    private ClubResponse response(Club c) { return ClubResponse.from(c,w.count(c)); }
}
