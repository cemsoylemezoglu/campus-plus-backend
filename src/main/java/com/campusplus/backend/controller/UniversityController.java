package com.campusplus.backend.controller;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.academic.*;
import com.campusplus.backend.service.UniversityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/universities")
public class UniversityController {
    private final UniversityService service;
    public UniversityController(UniversityService service) { this.service = service; }
    @GetMapping
    public PageResponse<UniversitySummary> list(@RequestParam(defaultValue = "") @Size(max = 200) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(query, page, size, includeInactive);
    }
    @GetMapping("/{id}")
    public UniversityResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping
    public ResponseEntity<UniversityResponse> create(@Valid @RequestBody CreateUniversityRequest request) {
        UniversityResponse result = service.create(request);
        return ResponseEntity.created(java.net.URI.create("/api/universities/" + result.id())).body(result);
    }
    @PatchMapping("/{id}")
    public UniversityResponse update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateUniversityRequest request) {
        return service.update(id, request);
    }
}
