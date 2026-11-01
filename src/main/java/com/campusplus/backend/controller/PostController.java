package com.campusplus.backend.controller;

import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.service.post.PostQueryService;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PostController {
    private final PostQueryService queries;
    public PostController(PostQueryService queries) { this.queries = queries; }
    @GetMapping("/api/feed")
    public ResponseEntity<FeedResponse> feed(@RequestParam(required = false) @Positive Long clubId,
            @RequestParam(required = false) @Size(max = 100) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return privateResponse(queries.feed(clubId, cursor, size));
    }
    @GetMapping("/api/posts/{id}")
    public ResponseEntity<PostResponse> get(@PathVariable @Positive Long id) { return privateResponse(queries.get(id)); }
    static <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    static ResponseEntity<ManagedPostResponse> created(ManagedPostResponse body) {
        return ResponseEntity.created(java.net.URI.create("/api/posts/" + body.content().post().id()))
                .cacheControl(CacheControl.noStore()).body(body);
    }
}
