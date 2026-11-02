package com.campusplus.backend.controller;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.dto.post.PostReasonRequest;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.service.comment.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/posts/{postId}/comments")
public class CommentController {
    private final CommentService service;private final CommentQueryService queries;private final CommentModerationService moderation;
    public CommentController(CommentService service,CommentQueryService queries,CommentModerationService moderation){this.service=service;this.queries=queries;this.moderation=moderation;}
    static <T> ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    private ResponseEntity<OwnCommentResponse> created(OwnCommentResponse c){return ResponseEntity.created(java.net.URI.create("/api/posts/"+c.postId()+"/comments/"+c.id())).cacheControl(CacheControl.noStore()).body(c);}
    @PostMapping public ResponseEntity<OwnCommentResponse> create(@PathVariable @Positive Long postId,@Valid @RequestBody CreateCommentRequest request){return created(service.create(postId,null,request));}
    @PostMapping("/{rootId}/replies") public ResponseEntity<OwnCommentResponse> reply(@PathVariable @Positive Long postId,@PathVariable @Positive Long rootId,@Valid @RequestBody CreateCommentRequest request){return created(service.create(postId,rootId,request));}
    @GetMapping public ResponseEntity<CommentPage> list(@PathVariable @Positive Long postId,@RequestParam(required=false) @Size(max=100) String cursor,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.list(postId,null,cursor,size));}
    @GetMapping("/{rootId}/replies") public ResponseEntity<CommentPage> replies(@PathVariable @Positive Long postId,@PathVariable @Positive Long rootId,@RequestParam(required=false) @Size(max=100) String cursor,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.list(postId,rootId,cursor,size));}
    @GetMapping("/{id}") public ResponseEntity<CommentResponse> get(@PathVariable @Positive Long postId,@PathVariable @Positive Long id){return privateResponse(queries.get(postId,id));}
    @PatchMapping("/{id}") public ResponseEntity<OwnCommentResponse> edit(@PathVariable @Positive Long postId,@PathVariable @Positive Long id,@Valid @RequestBody UpdateCommentRequest request){return privateResponse(service.update(postId,id,request));}
    @PostMapping("/{id}/hide") public ResponseEntity<CommentModerationResult> hide(@PathVariable @Positive Long postId,@PathVariable @Positive Long id,@Valid @RequestBody PostReasonRequest request){return privateResponse(moderation.resource(postId,id,true,request));}
    @PostMapping("/{id}/restore") public ResponseEntity<CommentModerationResult> restore(@PathVariable @Positive Long postId,@PathVariable @Positive Long id,@Valid @RequestBody PostReasonRequest request){return privateResponse(moderation.resource(postId,id,false,request));}
    @GetMapping("/{id}/moderation-actions") public ResponseEntity<PageResponse<CommentModerationResponse>> history(@PathVariable @Positive Long postId,@PathVariable @Positive Long id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.resourceHistory(postId,id,page,size));}
}
