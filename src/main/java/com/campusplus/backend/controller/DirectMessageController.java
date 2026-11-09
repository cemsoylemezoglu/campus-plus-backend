package com.campusplus.backend.controller;

import com.campusplus.backend.dto.messaging.*;
import com.campusplus.backend.dto.messaging.MessagingResponses.*;
import com.campusplus.backend.service.messaging.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/me")
public class DirectMessageController {
    private final DirectMessageService service;
    private final DirectMessageQueryService queries;
    public DirectMessageController(DirectMessageService service, DirectMessageQueryService queries) {
        this.service = service; this.queries = queries;
    }
    @PostMapping("/conversations")
    public ResponseEntity<Message> start(@Valid @RequestBody StartDirectConversationRequest request) { return response(service.start(request)); }
    @GetMapping("/conversations")
    public ResponseEntity<CursorPage<Conversation>> conversations(@RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) { return response(queries.conversations(cursor, limit)); }
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<CursorPage<Message>> messages(@PathVariable @Positive long id, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) { return response(queries.messages(id, cursor, limit)); }
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<Message> send(@PathVariable @Positive long id, @Valid @RequestBody SendDirectMessageRequest request) {
        return response(service.send(id, request));
    }
    @PutMapping("/conversations/{id}/read")
    public ResponseEntity<ReadState> read(@PathVariable @Positive long id, @Valid @RequestBody ReadDirectConversationRequest request) {
        return response(service.markRead(id, request));
    }
    @GetMapping("/conversations/unread-count") public ResponseEntity<UnreadCount> unread() { return response(queries.unread()); }
    @GetMapping("/blocks")
    public ResponseEntity<BlockPage> blocks(@RequestParam(required = false) @Positive Long beforeUserId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) { return response(queries.blocks(beforeUserId, limit)); }
    @PutMapping("/blocks/{userId}") public ResponseEntity<Void> block(@PathVariable @Positive long userId) {
        service.block(userId, true); return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
    @DeleteMapping("/blocks/{userId}") public ResponseEntity<Void> unblock(@PathVariable @Positive long userId) {
        service.block(userId, false); return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().header("Cache-Control", "no-store").body(body); }
}
