package com.campusplus.backend.controller;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.comment.OwnCommentResponse;
import com.campusplus.backend.dto.post.PostActionRequest;
import com.campusplus.backend.service.comment.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.campusplus.backend.controller.CommentController.privateResponse;
@RestController @RequestMapping("/api/me/comments")
public class OwnCommentController {
    private final CommentService service;private final CommentQueryService queries;
    public OwnCommentController(CommentService service,CommentQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping public ResponseEntity<PageResponse<OwnCommentResponse>> list(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.own(page,size));}
    @GetMapping("/{id}") public ResponseEntity<OwnCommentResponse> get(@PathVariable @Positive Long id){return privateResponse(queries.ownDetail(id));}
    @PostMapping("/{id}/remove") public ResponseEntity<OwnCommentResponse> remove(@PathVariable @Positive Long id,@Valid @RequestBody PostActionRequest request){return privateResponse(service.remove(id,request));}
}
