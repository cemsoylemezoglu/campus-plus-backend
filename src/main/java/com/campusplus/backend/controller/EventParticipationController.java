package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.EventParticipationStatus;
import com.campusplus.backend.service.event.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventParticipationController {
    private final EventParticipationService service;
    private final EventQueryService queries;
    public EventParticipationController(EventParticipationService service,EventQueryService queries){this.service=service;this.queries=queries;}
    @PostMapping("/api/events/{id}/participations")
    public ResponseEntity<MyEventParticipationResponse> register(@PathVariable @Positive Long id,@Valid @RequestBody RegisterEventRequest request) {
        var result=service.register(id,request);
        return ResponseEntity.status(request.participationVersion()==null?HttpStatus.CREATED:HttpStatus.OK).cacheControl(CacheControl.noStore()).body(result);
    }
    @PostMapping("/api/events/{id}/participation/cancel")
    public ResponseEntity<MyEventParticipationResponse> cancel(@PathVariable @Positive Long id,@Valid @RequestBody EventActionRequest request) {
        return privateResponse(service.cancel(id,request));
    }
    @GetMapping("/api/me/event-participations")
    public ResponseEntity<PageResponse<MyEventParticipationResponse>> own(@RequestParam(required=false) EventParticipationStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.own(status,page,size));
    }
    @GetMapping("/api/clubs/{clubId}/events/{id}/participations")
    public ResponseEntity<PageResponse<ManagedEventParticipationResponse>> managed(@PathVariable @Positive Long clubId,
            @PathVariable @Positive Long id,@RequestParam(required=false) EventParticipationStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.participants(clubId,id,status,page,size));
    }
    @PostMapping("/api/clubs/{clubId}/events/{id}/participations/{pid}/remove")
    public ResponseEntity<ManagedEventParticipationResponse> remove(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @PathVariable @Positive Long pid,@Valid @RequestBody EventReasonRequest request) {
        return privateResponse(service.decide(clubId,id,pid,new EventActionRequest(request.version()),request.reason(),EventParticipationService.Decision.REMOVE));
    }
    @PostMapping("/api/clubs/{clubId}/events/{id}/participations/{pid}/allow-reregistration")
    public ResponseEntity<ManagedEventParticipationResponse> allow(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @PathVariable @Positive Long pid,@Valid @RequestBody EventActionRequest request) {
        return privateResponse(service.decide(clubId,id,pid,request,null,EventParticipationService.Decision.ALLOW_REREGISTRATION));
    }
    @PostMapping("/api/clubs/{clubId}/events/{id}/participations/{pid}/check-in")
    public ResponseEntity<ManagedEventParticipationResponse> checkIn(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @PathVariable @Positive Long pid,@Valid @RequestBody EventActionRequest request) {
        return privateResponse(service.decide(clubId,id,pid,request,null,EventParticipationService.Decision.CHECK_IN));
    }
    private <T> ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
