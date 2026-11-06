package com.campusplus.backend.controller;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.dto.comment.CommentModerationResponse;
import com.campusplus.backend.dto.post.PostReasonRequest;
import com.campusplus.backend.entity.CommentStatus;
import com.campusplus.backend.service.clubinteraction.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.campusplus.backend.controller.CommentController.privateResponse;
@RestController @RequestMapping("/api/admin/club-comments")
public class ClubCommentModerationController {
    private final ClubCommentModerationService service;private final ClubCommentQueryService queries;
    public ClubCommentModerationController(ClubCommentModerationService service,ClubCommentQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping public ResponseEntity<PageResponse<AdminClubCommentResponse>> list(@RequestParam(required=false) CommentStatus status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.managed(status,page,size));}
    @GetMapping("/{id}") public ResponseEntity<AdminClubCommentResponse> get(@PathVariable @Positive Long id){return privateResponse(queries.adminDetail(id));}
    @PostMapping("/{id}/hide") public ResponseEntity<OwnClubCommentResponse> hide(@PathVariable @Positive Long id,@Valid @RequestBody PostReasonRequest request){return privateResponse(service.platform(id,true,request));}
    @PostMapping("/{id}/restore") public ResponseEntity<OwnClubCommentResponse> restore(@PathVariable @Positive Long id,@Valid @RequestBody PostReasonRequest request){return privateResponse(service.platform(id,false,request));}
    @GetMapping("/{id}/moderation-actions") public ResponseEntity<PageResponse<CommentModerationResponse>> history(@PathVariable @Positive Long id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.adminHistory(id,page,size));}
}
