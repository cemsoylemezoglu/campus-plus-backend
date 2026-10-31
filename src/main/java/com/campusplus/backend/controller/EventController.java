package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.EventStatus;
import com.campusplus.backend.service.event.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventController {
    private final EventService service;
    private final EventQueryService queries;
    public EventController(EventService service,EventQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping("/api/events")
    public ResponseEntity<PageResponse<EventSummary>> discover(@RequestParam(required=false) @Positive Long clubId,
            @RequestParam(required=false) @Positive Long universityId,@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to,@RequestParam(defaultValue="") @Size(max=200) String query,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.discover(clubId,universityId,from,to,query,page,size));
    }
    @GetMapping("/api/events/{id}")
    public ResponseEntity<EventResponse> get(@PathVariable @Positive Long id){return privateResponse(queries.get(id));}
    @GetMapping("/api/clubs/{clubId}/events/manage")
    public ResponseEntity<PageResponse<EventSummary>> managed(@PathVariable @Positive Long clubId,
            @RequestParam(required=false) EventStatus status,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.managed(clubId,status,page,size));}
    @PostMapping("/api/clubs/{clubId}/events")
    public ResponseEntity<EventResponse> create(@PathVariable @Positive Long clubId,@Valid @RequestBody CreateEventRequest request) {
        var result=service.create(clubId,request);
        return ResponseEntity.created(URI.create("/api/events/"+result.event().id())).cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping("/api/clubs/{clubId}/events/{id}")
    public ResponseEntity<EventResponse> update(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody UpdateEventRequest request){return privateResponse(service.update(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/events/{id}/publish")
    public ResponseEntity<EventResponse> publish(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody EventActionRequest request){return privateResponse(service.publish(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/events/{id}/reschedule")
    public ResponseEntity<EventResponse> reschedule(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody RescheduleEventRequest request){return privateResponse(service.reschedule(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/events/{id}/cancel")
    public ResponseEntity<EventResponse> cancel(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody EventReasonRequest request){return privateResponse(service.cancel(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/events/{id}/complete")
    public ResponseEntity<EventResponse> complete(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody EventActionRequest request){return privateResponse(service.complete(clubId,id,request));}
    @PostMapping("/api/clubs/{clubId}/events/{id}/archive")
    public ResponseEntity<EventResponse> archive(@PathVariable @Positive Long clubId,@PathVariable @Positive Long id,
            @Valid @RequestBody EventActionRequest request){return privateResponse(service.archive(clubId,id,request));}
    private <T> ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
