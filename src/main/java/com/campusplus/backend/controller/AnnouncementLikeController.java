package com.campusplus.backend.controller;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.service.clubinteraction.*;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.campusplus.backend.controller.CommentController.privateResponse;
@RestController
public class AnnouncementLikeController {
    private final ClubLikeService service;private final ClubCommentQueryService queries;
    public AnnouncementLikeController(ClubLikeService service,ClubCommentQueryService queries){this.service=service;this.queries=queries;}
    private ClubSourceRef ref(long id){return new ClubSourceRef(ClubSourceKind.ANNOUNCEMENT,id);}
    @PutMapping("/api/announcements/{sourceId}/like") public ResponseEntity<LikeState> sourceLike(@PathVariable @Positive Long sourceId){return privateResponse(service.source(ref(sourceId)));}
    @DeleteMapping("/api/announcements/{sourceId}/like") public ResponseEntity<Void> unlikeSource(@PathVariable @Positive Long sourceId){service.unlikeSource(ref(sourceId));return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @PutMapping("/api/announcements/{sourceId}/comments/{id}/like") public ResponseEntity<LikeState> comment(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id){return privateResponse(service.comment(ref(sourceId),id));}
    @DeleteMapping("/api/announcements/{sourceId}/comments/{id}/like") public ResponseEntity<Void> unlikeComment(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id){service.unlikeComment(ref(sourceId),id);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @GetMapping("/api/announcements/{sourceId}/interactions") public ResponseEntity<ClubInteractionSummary> summary(@PathVariable @Positive Long sourceId){return privateResponse(queries.interactions(ref(sourceId)));}
}
