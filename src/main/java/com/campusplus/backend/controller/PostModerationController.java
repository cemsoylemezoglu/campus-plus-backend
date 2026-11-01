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

@RestController @RequestMapping("/api/admin/posts")
public class PostModerationController {
    private final PostModerationService service;
    private final PostQueryService queries;
    public PostModerationController(PostModerationService service, PostQueryService queries) { this.service = service; this.queries = queries; }
    @GetMapping
    public ResponseEntity<PageResponse<AdminPostResponse>> list(@RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) Boolean hidden, @RequestParam(required = false) @Positive Long universityId,
            @RequestParam(required = false) @Positive Long clubId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.moderation(status, hidden, universityId, clubId, page, size));
    }
    @GetMapping("/{id}")
    public ResponseEntity<AdminPostResponse> detail(@PathVariable @Positive Long id) { return privateResponse(queries.adminDetail(id)); }
    @GetMapping("/{id}/moderation-actions")
    public ResponseEntity<PageResponse<PostModerationResponse>> history(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.history(id, page, size));
    }
    @PostMapping("/{id}/hide")
    public ResponseEntity<AdminPostResponse> hide(@PathVariable @Positive Long id, @Valid @RequestBody PostReasonRequest request) {
        return privateResponse(service.moderate(id, true, request));
    }
    @PostMapping("/{id}/restore")
    public ResponseEntity<AdminPostResponse> restore(@PathVariable @Positive Long id, @Valid @RequestBody PostReasonRequest request) {
        return privateResponse(service.moderate(id, false, request));
    }
}
