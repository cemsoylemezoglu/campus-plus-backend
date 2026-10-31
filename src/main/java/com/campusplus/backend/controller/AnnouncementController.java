package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.announcement.*;
import com.campusplus.backend.entity.AnnouncementStatus;
import com.campusplus.backend.service.announcement.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnnouncementController {
    private final AnnouncementService service;
    private final AnnouncementQueryService queries;
    public AnnouncementController(AnnouncementService service,AnnouncementQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping("/api/announcements")
    public ResponseEntity<PageResponse<AnnouncementSummary>> discover(@RequestParam(required=false) @Positive Long clubId,
            @RequestParam(required=false) @Positive Long universityId,@RequestParam(defaultValue="") @Size(max=200) String query,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.discover(clubId,universityId,query,page,size));
    }
    @GetMapping("/api/announcements/{id}")
    public ResponseEntity<AnnouncementResponse> get(@PathVariable @Positive Long id){return privateResponse(queries.get(id));}
    @GetMapping("/api/clubs/{clubId}/announcements/manage")
    public ResponseEntity<PageResponse<ManagedAnnouncementSummary>> managed(@PathVariable @Positive Long clubId,
            @RequestParam(required=false) AnnouncementStatus status,@RequestParam(required=false) Boolean archived,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.managed(clubId,status,archived,page,size));
    }
    @GetMapping("/api/clubs/{clubId}/announcements/{id}/manage")
    public ResponseEntity<ManagedAnnouncementResponse> managedDetail(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id) {
        return privateResponse(queries.managedDetail(clubId,id));
    }
    @PostMapping("/api/clubs/{clubId}/announcements")
    public ResponseEntity<ManagedAnnouncementResponse> create(@PathVariable @Positive Long clubId,@Valid @RequestBody CreateAnnouncementRequest request) {
        var result=service.create(clubId,request);
        return ResponseEntity.created(URI.create("/api/announcements/"+result.announcement().id()))
            .cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping("/api/clubs/{clubId}/announcements/{id}")
    public ResponseEntity<ManagedAnnouncementResponse> update(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody UpdateAnnouncementRequest request){return privateResponse(service.update(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/announcements/{id}/publish")
    public ResponseEntity<ManagedAnnouncementResponse> publish(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody AnnouncementActionRequest request){return privateResponse(service.publish(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/announcements/{id}/withdraw")
    public ResponseEntity<ManagedAnnouncementResponse> withdraw(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody WithdrawAnnouncementRequest request){return privateResponse(service.withdraw(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/announcements/{id}/archive")
    public ResponseEntity<ManagedAnnouncementResponse> archive(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody AnnouncementActionRequest request){return privateResponse(service.archive(clubId,id,request));}
    private <T> ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
