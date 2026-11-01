package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.entity.PostStatus;
import com.campusplus.backend.service.post.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.campusplus.backend.controller.PostController.*;

@RestController @RequestMapping("/api/clubs/{clubId}/posts")
public class ClubPostController {
    private final PostService service;
    private final PostQueryService queries;
    public ClubPostController(PostService service, PostQueryService queries) { this.service = service; this.queries = queries; }
    @PostMapping
    public ResponseEntity<ManagedPostResponse> create(@PathVariable @Positive Long clubId, @Valid @RequestBody CreateClubPostRequest request) {
        return created(service.createClub(clubId, request));
    }
    @GetMapping("/manage")
    public ResponseEntity<PageResponse<ManagedPostResponse>> list(@PathVariable @Positive Long clubId,
            @RequestParam(required = false) PostStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return privateResponse(queries.club(clubId, status, page, size)); }
    @GetMapping("/{id}/manage")
    public ResponseEntity<ManagedPostResponse> detail(@PathVariable @Positive Long clubId, @PathVariable @Positive Long id) {
        return privateResponse(queries.clubDetail(clubId, id));
    }
    @PatchMapping("/{id}")
    public ResponseEntity<ManagedPostResponse> update(@PathVariable @Positive Long clubId, @PathVariable @Positive Long id,
            @Valid @RequestBody UpdatePostRequest request) { return privateResponse(service.update(clubId, id, request)); }
    @PostMapping("/{id}/publish")
    public ResponseEntity<ManagedPostResponse> publish(@PathVariable @Positive Long clubId, @PathVariable @Positive Long id,
            @Valid @RequestBody PostActionRequest request) { return privateResponse(service.publish(clubId, id, request)); }
    @PostMapping("/{id}/remove")
    public ResponseEntity<ManagedPostResponse> remove(@PathVariable @Positive Long clubId, @PathVariable @Positive Long id,
            @Valid @RequestBody PostReasonRequest request) { return privateResponse(service.remove(clubId, id, request)); }
}
