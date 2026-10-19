package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.club.*;
import com.campusplus.backend.entity.ClubStatus;
import com.campusplus.backend.service.club.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClubController {
    private final ClubService service;
    private final ClubQueryService queries;
    public ClubController(ClubService service,ClubQueryService queries) { this.service=service; this.queries=queries; }
    @GetMapping("/api/clubs")
    public PageResponse<ClubSummary> list(@RequestParam(required=false) @Positive Long universityId,
            @RequestParam(defaultValue="") @Size(max=200) String query,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return queries.list(universityId,query,page,size);
    }
    @GetMapping("/api/clubs/{id}")
    public ClubResponse get(@PathVariable @Positive Long id) { return queries.get(id); }
    @GetMapping("/api/clubs/{id}/review")
    public ResponseEntity<ClubReviewResponse> review(@PathVariable @Positive Long id) { return privateResponse(queries.review(id)); }
    @PostMapping("/api/clubs")
    public ResponseEntity<ClubResponse> create(@Valid @RequestBody CreateClubRequest request) {
        var result=service.create(request);
        return ResponseEntity.created(java.net.URI.create("/api/clubs/"+result.club().id())).cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping("/api/clubs/{id}")
    public ResponseEntity<ClubResponse> update(@PathVariable @Positive Long id,@Valid @RequestBody UpdateClubRequest request) {
        return privateResponse(service.update(id,request));
    }
    @PostMapping("/api/clubs/{id}/resubmit")
    public ResponseEntity<ClubResponse> resubmit(@PathVariable @Positive Long id,@Valid @RequestBody ClubActionRequest request) {
        return privateResponse(service.resubmit(id,request));
    }
    @PostMapping("/api/clubs/{id}/archive")
    public ResponseEntity<ClubResponse> archive(@PathVariable @Positive Long id,@Valid @RequestBody ClubActionRequest request) {
        return privateResponse(service.archive(id,request));
    }
    @GetMapping("/api/admin/clubs")
    public ResponseEntity<PageResponse<ClubReviewResponse>> queue(@RequestParam(required=false) ClubStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.adminList(status,page,size));
    }
    @PostMapping("/api/admin/clubs/{id}/approve")
    public ResponseEntity<ClubResponse> approve(@PathVariable @Positive Long id,@Valid @RequestBody ClubActionRequest request) {
        return privateResponse(service.review(id,request,ClubService.ReviewAction.APPROVE));
    }
    @PostMapping("/api/admin/clubs/{id}/reject")
    public ResponseEntity<ClubResponse> reject(@PathVariable @Positive Long id,@Valid @RequestBody ClubActionRequest request) {
        return privateResponse(service.review(id,request,ClubService.ReviewAction.REJECT));
    }
    @PostMapping("/api/admin/clubs/{id}/suspend")
    public ResponseEntity<ClubResponse> suspend(@PathVariable @Positive Long id,@Valid @RequestBody ClubActionRequest request) {
        return privateResponse(service.review(id,request,ClubService.ReviewAction.SUSPEND));
    }
    @PostMapping("/api/admin/clubs/{id}/restore")
    public ResponseEntity<ClubResponse> restore(@PathVariable @Positive Long id,@Valid @RequestBody ClubActionRequest request) {
        return privateResponse(service.review(id,request,ClubService.ReviewAction.RESTORE));
    }
    private <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
