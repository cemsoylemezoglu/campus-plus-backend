package com.campusplus.backend.controller;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.dto.post.PostReasonRequest;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.service.clubinteraction.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/announcements/{sourceId}/comments")
public class AnnouncementCommentController {
    private final ClubCommentService service;private final ClubCommentQueryService queries;private final ClubCommentModerationService moderation;
    public AnnouncementCommentController(ClubCommentService service,ClubCommentQueryService queries,ClubCommentModerationService moderation){this.service=service;this.queries=queries;this.moderation=moderation;}
    private ClubSourceRef ref(long id){return new ClubSourceRef(ClubSourceKind.ANNOUNCEMENT,id);}
    static <T> ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    private ResponseEntity<OwnClubCommentResponse> created(OwnClubCommentResponse c){return ResponseEntity.created(java.net.URI.create("/api/announcements/"+c.source().id()+"/comments/"+c.id())).cacheControl(CacheControl.noStore()).body(c);}
    @PostMapping public ResponseEntity<OwnClubCommentResponse> create(@PathVariable @Positive Long sourceId,@Valid @RequestBody CreateCommentRequest request){return created(service.create(ref(sourceId),null,request));}
    @PostMapping("/{rootId}/replies") public ResponseEntity<OwnClubCommentResponse> reply(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long rootId,@Valid @RequestBody CreateCommentRequest request){return created(service.create(ref(sourceId),rootId,request));}
    @GetMapping public ResponseEntity<ClubCommentPage> list(@PathVariable @Positive Long sourceId,@RequestParam(required=false) @Size(max=100) String cursor,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.list(ref(sourceId),null,cursor,size));}
    @GetMapping("/{rootId}/replies") public ResponseEntity<ClubCommentPage> replies(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long rootId,@RequestParam(required=false) @Size(max=100) String cursor,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.list(ref(sourceId),rootId,cursor,size));}
    @GetMapping("/{id}") public ResponseEntity<ClubCommentResponse> get(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id){return privateResponse(queries.get(ref(sourceId),id));}
    @PatchMapping("/{id}") public ResponseEntity<OwnClubCommentResponse> edit(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id,@Valid @RequestBody UpdateCommentRequest request){return privateResponse(service.update(ref(sourceId),id,request));}
    @PostMapping("/{id}/hide") public ResponseEntity<ClubCommentModerationResult> hide(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id,@Valid @RequestBody PostReasonRequest request){return privateResponse(moderation.resource(ref(sourceId),id,true,request));}
    @PostMapping("/{id}/restore") public ResponseEntity<ClubCommentModerationResult> restore(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id,@Valid @RequestBody PostReasonRequest request){return privateResponse(moderation.resource(ref(sourceId),id,false,request));}
    @GetMapping("/{id}/moderation-actions") public ResponseEntity<PageResponse<CommentModerationResponse>> history(@PathVariable @Positive Long sourceId,@PathVariable @Positive Long id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return privateResponse(queries.resourceHistory(ref(sourceId),id,page,size));}
}
