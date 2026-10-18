package com.campusplus.backend.controller;

import com.campusplus.backend.dto.academic.*;
import com.campusplus.backend.service.StudentProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class StudentProfileController {
    private final StudentProfileService service;
    public StudentProfileController(StudentProfileService service) { this.service = service; }
    @GetMapping("/api/me/student-profile")
    public ResponseEntity<StudentProfileResponse> getOwn() { return privateResponse(service.getOwn()); }
    @PostMapping("/api/me/student-profile")
    public ResponseEntity<StudentProfileResponse> createOwn(@Valid @RequestBody CreateStudentProfileRequest request) {
        return ResponseEntity.created(java.net.URI.create("/api/me/student-profile"))
                .cacheControl(CacheControl.noStore()).body(service.createOwn(request));
    }
    @PutMapping("/api/me/student-profile")
    public ResponseEntity<StudentProfileResponse> updateOwn(@Valid @RequestBody UpdateStudentProfileRequest request) {
        return privateResponse(service.updateOwn(request));
    }
    @GetMapping("/api/admin/users/{userId}/student-profile")
    public ResponseEntity<StudentProfileResponse> getForAdmin(@PathVariable @Positive Long userId) {
        return privateResponse(service.getForAdmin(userId));
    }
    @PutMapping("/api/admin/users/{userId}/student-profile")
    public ResponseEntity<StudentProfileResponse> updateForAdmin(@PathVariable @Positive Long userId,
            @Valid @RequestBody UpdateStudentProfileRequest request) {
        return privateResponse(service.updateForAdmin(userId, request));
    }
    private ResponseEntity<StudentProfileResponse> privateResponse(StudentProfileResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
