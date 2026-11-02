package com.campusplus.backend.controller;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.service.comment.*;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.campusplus.backend.controller.CommentController.privateResponse;
@RestController
public class LikeController {
    private final LikeService service;private final CommentQueryService queries;
    public LikeController(LikeService service,CommentQueryService queries){this.service=service;this.queries=queries;}
    @PutMapping("/api/posts/{postId}/like") public ResponseEntity<LikeState> post(@PathVariable @Positive Long postId){return privateResponse(service.post(postId));}
    @DeleteMapping("/api/posts/{postId}/like") public ResponseEntity<Void> unlikePost(@PathVariable @Positive Long postId){service.unlikePost(postId);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @PutMapping("/api/posts/{postId}/comments/{id}/like") public ResponseEntity<LikeState> comment(@PathVariable @Positive Long postId,@PathVariable @Positive Long id){return privateResponse(service.comment(postId,id));}
    @DeleteMapping("/api/posts/{postId}/comments/{id}/like") public ResponseEntity<Void> unlikeComment(@PathVariable @Positive Long postId,@PathVariable @Positive Long id){service.unlikeComment(postId,id);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @GetMapping("/api/posts/{postId}/interactions") public ResponseEntity<InteractionSummary> summary(@PathVariable @Positive Long postId){return privateResponse(queries.interactions(postId));}
}
