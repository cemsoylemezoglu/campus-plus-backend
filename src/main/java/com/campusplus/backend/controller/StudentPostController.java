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

@RestController @RequestMapping("/api/me/posts")
public class StudentPostController {
    private final PostService service;
    private final PostQueryService queries;
    public StudentPostController(PostService service, PostQueryService queries) { this.service = service; this.queries = queries; }
    @PostMapping
    public ResponseEntity<ManagedPostResponse> create(@Valid @RequestBody CreateStudentPostRequest request) { return created(service.createStudent(request)); }
    @GetMapping
    public ResponseEntity<PageResponse<ManagedPostResponse>> list(@RequestParam(required = false) PostStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.own(status, page, size));
    }
    @GetMapping("/{id}/manage")
    public ResponseEntity<ManagedPostResponse> detail(@PathVariable @Positive Long id) { return privateResponse(queries.ownDetail(id)); }
    @PatchMapping("/{id}")
    public ResponseEntity<ManagedPostResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody UpdatePostRequest request) {
        return privateResponse(service.update(null, id, request));
    }
    @PostMapping("/{id}/publish")
    public ResponseEntity<ManagedPostResponse> publish(@PathVariable @Positive Long id, @Valid @RequestBody PostActionRequest request) {
        return privateResponse(service.publish(null, id, request));
    }
    @PostMapping("/{id}/remove")
    public ResponseEntity<ManagedPostResponse> remove(@PathVariable @Positive Long id, @Valid @RequestBody PostReasonRequest request) {
        return privateResponse(service.remove(null, id, request));
    }
}
