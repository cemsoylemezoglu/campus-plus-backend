package com.campusplus.backend.controller;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.notification.*;
import com.campusplus.backend.service.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/me/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public ResponseEntity<PageResponse<NotificationResponse>> inbox(@RequestParam(defaultValue="false") boolean unreadOnly,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return privateResponse(service.inbox(unreadOnly,page,size));
    }
    @GetMapping("/unread-count") public ResponseEntity<UnreadCountResponse> count(){return privateResponse(service.unreadCount());}
    @PostMapping("/{id}/read") public ResponseEntity<NotificationResponse> read(@PathVariable @Positive Long id,@Valid @RequestBody NotificationReadRequest request){return privateResponse(service.read(id,request));}
    @PostMapping("/{id}/unread") public ResponseEntity<NotificationResponse> unread(@PathVariable @Positive Long id,@Valid @RequestBody NotificationReadRequest request){return privateResponse(service.unread(id,request));}
    @PostMapping("/read-all") public ResponseEntity<ReadAllNotificationsResponse> all(@Valid @RequestBody ReadAllNotificationsRequest request){return privateResponse(service.readAll(request));}
    private <T> ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
