package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.club.*;
import com.campusplus.backend.entity.ClubMembershipStatus;
import com.campusplus.backend.service.club.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClubMembershipController {
    private final ClubMembershipService service;
    private final ClubQueryService queries;
    public ClubMembershipController(ClubMembershipService service,ClubQueryService queries) { this.service=service; this.queries=queries; }
    @PostMapping("/api/clubs/{clubId}/membership-requests")
    public ResponseEntity<MyMembershipResponse> apply(@PathVariable @Positive Long clubId,
            @Valid @RequestBody(required=false) MembershipApplyRequest request) {
        MembershipApplyRequest input=request==null ? new MembershipApplyRequest(null) : request;
        var result=service.apply(clubId,input);
        return ResponseEntity.status(input.version()==null?HttpStatus.CREATED:HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping("/api/me/club-memberships")
    public ResponseEntity<PageResponse<MyMembershipResponse>> own(@RequestParam(required=false) ClubMembershipStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.own(status,page,size));
    }
    @GetMapping("/api/clubs/{clubId}/memberships")
    public ResponseEntity<PageResponse<ManagedMembershipResponse>> managed(@PathVariable @Positive Long clubId,
            @RequestParam(required=false) ClubMembershipStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.managed(clubId,status,page,size));
    }
    @PostMapping("/api/clubs/{clubId}/membership/cancel")
    public ResponseEntity<MyMembershipResponse> cancel(@PathVariable @Positive Long clubId,@Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.cancel(clubId,request));
    }
    @PostMapping("/api/clubs/{clubId}/membership/leave")
    public ResponseEntity<MyMembershipResponse> leave(@PathVariable @Positive Long clubId,@Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.leave(clubId,request));
    }
    @PostMapping("/api/clubs/{clubId}/memberships/{memberId}/approve")
    public ResponseEntity<ManagedMembershipResponse> approve(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.decide(clubId,memberId,request,ClubMembershipService.Decision.APPROVE));
    }
    @PostMapping("/api/clubs/{clubId}/memberships/{memberId}/reject")
    public ResponseEntity<ManagedMembershipResponse> reject(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.decide(clubId,memberId,request,ClubMembershipService.Decision.REJECT));
    }
    @PostMapping("/api/clubs/{clubId}/memberships/{memberId}/remove")
    public ResponseEntity<ManagedMembershipResponse> remove(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.decide(clubId,memberId,request,ClubMembershipService.Decision.REMOVE));
    }
    @PostMapping("/api/clubs/{clubId}/memberships/{memberId}/allow-reapplication")
    public ResponseEntity<ManagedMembershipResponse> allowReapplication(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.decide(clubId,memberId,request,ClubMembershipService.Decision.ALLOW_REAPPLICATION));
    }
    @PostMapping("/api/admin/clubs/{clubId}/memberships/{memberId}/suspend")
    public ResponseEntity<ManagedMembershipResponse> suspend(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.decide(clubId,memberId,request,ClubMembershipService.Decision.SUSPEND));
    }
    @PostMapping("/api/admin/clubs/{clubId}/memberships/{memberId}/restore")
    public ResponseEntity<ManagedMembershipResponse> restore(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody MembershipDecisionRequest request) {
        return privateResponse(service.decide(clubId,memberId,request,ClubMembershipService.Decision.RESTORE));
    }
    @PatchMapping("/api/clubs/{clubId}/memberships/{memberId}/role")
    public ResponseEntity<ManagedMembershipResponse> role(@PathVariable @Positive Long clubId,@PathVariable @Positive Long memberId,
            @Valid @RequestBody ChangeMembershipRoleRequest request) {
        return privateResponse(service.role(clubId,memberId,request));
    }
    @PostMapping("/api/clubs/{clubId}/ownership-transfer")
    public ResponseEntity<ClubResponse> transfer(@PathVariable @Positive Long clubId,@Valid @RequestBody TransferOwnershipRequest request) {
        return privateResponse(service.transfer(clubId,request));
    }
    private <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
