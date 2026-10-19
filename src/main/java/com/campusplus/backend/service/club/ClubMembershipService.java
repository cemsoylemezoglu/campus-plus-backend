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
import static com.campusplus.backend.entity.ClubMembershipRole.*;
import static com.campusplus.backend.entity.ClubMembershipStatus.*;

@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class ClubMembershipService {
    private final ClubWorkspace w;
    private final ClubSettings settings;
    public ClubMembershipService(ClubWorkspace w,ClubSettings settings) { this.w=w; this.settings=settings; }
    public MyMembershipResponse apply(@Positive Long clubId,@Valid MembershipApplyRequest request) {
        var ctx=w.lock(clubId,List.of()); Club c=ctx.club(); w.policy.requireActive(c);
        StudentProfile profile=w.eligible(ctx.actor().getId(),c);
        ClubMembership m=w.local(c,ctx.actor().getId());
        if(m==null) {
            if(request.version()!=null) throw new ResourceConflictException("Başvuru bulunamadı; sürüm gönderilmemeli.");
            m=new ClubMembership(c,profile,false,w.clock.instant());
        } else {
            m=w.self(c); ClubWorkspace.version(m.getVersion(),request.version());
            if(m.getStatus()!=LEFT && m.getStatus()!=REJECTED) throw new ResourceConflictException("Mevcut üyelik durumunda başvuru yapılamaz.");
            if(w.clock.instant().isBefore(m.getUpdatedAt().plus(settings.reapplyCooldown())))
                throw new ResourceConflictException("Tekrar başvuru için bekleme süresi dolmadı.");
            m.requestAgain(w.clock.instant());
        }
        return own(w.memberships.saveAndFlush(m));
    }
    public MyMembershipResponse cancel(@Positive Long clubId,@Valid MembershipDecisionRequest request) {
        var ctx=w.lock(clubId,List.of()); var m=w.self(ctx.club());
        ClubWorkspace.version(m.getVersion(),request.version());
        if(m.getStatus()!=PENDING) throw new ResourceConflictException("Bekleyen başvuru bulunamadı.");
        m.transition(LEFT,ctx.actor(),null,w.clock.instant()); return own(w.memberships.saveAndFlush(m));
    }
    public MyMembershipResponse leave(@Positive Long clubId,@Valid MembershipDecisionRequest request) {
        var ctx=w.lock(clubId,List.of()); var m=w.self(ctx.club());
        ClubWorkspace.version(m.getVersion(),request.version());
        if(m.getStatus()!=ACTIVE && m.getStatus()!=SUSPENDED) throw new ResourceConflictException("Ayrılabileceğiniz üyelik bulunamadı.");
        w.membershipPolicy.protectOwner(ctx.club(),m);
        m.transition(LEFT,ctx.actor(),null,w.clock.instant()); w.memberships.saveAndFlush(m); w.syncRole(ctx.actor());
        return own(m);
    }
    public enum Decision { APPROVE, REJECT, REMOVE, ALLOW_REAPPLICATION, SUSPEND, RESTORE }
    public ManagedMembershipResponse decide(@Positive Long clubId,@Positive Long memberId,
                                             @Valid MembershipDecisionRequest request,Decision action) {
        w.preflightManagement(clubId,false);
        var preview=w.memberships.findByIdAndClubId(memberId,clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Üyelik bulunamadı."));
        var ctx=w.lock(clubId,List.of(preview.getUserId())); var c=ctx.club();
        var local=w.local(c,ctx.actor().getId()); w.policy.requireManager(ctx.actor(),local);
        if(action==Decision.SUSPEND || action==Decision.RESTORE) w.policy.requireAdmin(ctx.actor());
        var m=w.member(clubId,memberId); ClubWorkspace.version(m.getVersion(),request.version());
        if(c.getStatus()==ClubStatus.ARCHIVED) throw new ResourceConflictException("Kulüp arşivlenmiş.");
        if(action!=Decision.SUSPEND) w.policy.requireActive(c);
        switch(action) {
            case APPROVE -> { require(m,PENDING); w.eligible(m.getUserId(),c); m.transition(ACTIVE,ctx.actor(),request.reason(),w.clock.instant()); }
            case REJECT -> { require(m,PENDING); m.transition(REJECTED,ctx.actor(),ClubWorkspace.reason(request.reason()),w.clock.instant()); }
            case REMOVE -> {
                require(m,ACTIVE); w.membershipPolicy.protectOwner(c,m);
                if(m.getRole()==MANAGER) w.policy.requireOwner(ctx.actor(),local);
                m.transition(REMOVED,ctx.actor(),ClubWorkspace.reason(request.reason()),w.clock.instant());
            }
            case ALLOW_REAPPLICATION -> { require(m,REMOVED); m.transition(REJECTED,ctx.actor(),request.reason(),w.clock.instant()); }
            case SUSPEND -> { require(m,ACTIVE); w.membershipPolicy.protectOwner(c,m); m.transition(SUSPENDED,ctx.actor(),ClubWorkspace.reason(request.reason()),w.clock.instant()); }
            case RESTORE -> { require(m,SUSPENDED); w.eligible(m.getUserId(),c); m.transition(ACTIVE,ctx.actor(),request.reason(),w.clock.instant()); }
        }
        w.memberships.saveAndFlush(m); w.syncRole(ctx.users().get(m.getUserId())); return managed(m,ctx.users().get(m.getUserId()));
    }
    public ManagedMembershipResponse role(@Positive Long clubId,@Positive Long memberId,@Valid ChangeMembershipRoleRequest request) {
        w.preflightManagement(clubId,true);
        var preview=w.memberships.findByIdAndClubId(memberId,clubId).orElseThrow(() -> new ResourceNotFoundException("Üyelik bulunamadı."));
        var ctx=w.lock(clubId,List.of(preview.getUserId())); var c=ctx.club(); w.policy.requireActive(c);
        w.policy.requireOwner(ctx.actor(),w.local(c,ctx.actor().getId()));
        var m=w.member(clubId,memberId); ClubWorkspace.version(m.getVersion(),request.version()); require(m,ACTIVE);
        w.membershipPolicy.protectOwner(c,m);
        if(request.role()==null || request.role()==OWNER) throw new InvalidRequestException("Sahiplik devri için ayrı endpoint kullanınız.");
        if(m.getRole()==request.role()) throw new ResourceConflictException("Üyelik zaten bu rolde.");
        // Removing a privilege must remain possible when the university is inactive.
        if(request.role()==MANAGER) w.eligible(m.getUserId(),c);
        m.changeRole(request.role(),w.clock.instant()); w.memberships.saveAndFlush(m); w.syncRole(ctx.users().get(m.getUserId()));
        return managed(m,ctx.users().get(m.getUserId()));
    }
    public ClubResponse transfer(@Positive Long clubId,@Valid TransferOwnershipRequest request) {
        w.preflightManagement(clubId,true);
        var old=w.memberships.activeOwner(clubId).orElseThrow(() -> new ResourceConflictException("Kulüp sahibi bulunamadı."));
        var targetPreview=w.memberships.findByIdAndClubId(request.targetMembershipId(),clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Hedef üyelik bulunamadı."));
        var ctx=w.lock(clubId,List.of(old.getUserId(),targetPreview.getUserId())); var c=ctx.club(); w.policy.requireActive(c);
        w.policy.requireOwner(ctx.actor(),w.local(c,ctx.actor().getId())); ClubWorkspace.version(c.getVersion(),request.clubVersion());
        var owner=w.memberships.activeOwner(clubId).orElseThrow(() -> new ResourceConflictException("Kulüp sahibi değişti."));
        if(!owner.getId().equals(old.getId())) throw new ResourceConflictException("Sahip değişti; tekrar deneyiniz.");
        owner=w.member(clubId,owner.getId()); var target=w.member(clubId,request.targetMembershipId());
        ClubWorkspace.version(owner.getVersion(),request.ownerMembershipVersion());
        ClubWorkspace.version(target.getVersion(),request.targetMembershipVersion()); require(target,ACTIVE);
        if(owner.getId().equals(target.getId())) throw new ResourceConflictException("Üye zaten kulüp sahibi.");
        w.eligible(target.getUserId(),c);
        owner.changeRole(MANAGER,w.clock.instant());
        w.memberships.saveAndFlush(owner); // Downgrade first; partial unique OWNER index remains satisfied.
        target.changeRole(OWNER,w.clock.instant()); w.memberships.saveAndFlush(target);
        w.syncRole(ctx.users().get(owner.getUserId())); w.syncRole(ctx.users().get(target.getUserId()));
        return ClubResponse.from(c,w.count(c));
    }
    private void require(ClubMembership m,ClubMembershipStatus status) {
        if(m.getStatus()!=status) throw new ResourceConflictException("Üyelik durum geçişi yapılamaz.");
    }
    private MyMembershipResponse own(ClubMembership m) { return MyMembershipResponse.from(m,w.count(m.getClub())); }
    private ManagedMembershipResponse managed(ClubMembership m,User user) {
        return new ManagedMembershipResponse(m.getId(),m.getUserId(),user.getName(),m.getRole(),m.getStatus(),m.getRequestedAt(),m.getJoinedAt(),m.getEndedAt(),m.getVersion());
    }
}
