package com.campusplus.backend.service;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.academic.*;
import com.campusplus.backend.entity.University;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.UniversityRepository;
import com.campusplus.backend.security.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly = true)
public class UniversityService {
    private final UniversityRepository universities;
    private final CurrentActor actor;
    private final Clock clock;
    public UniversityService(UniversityRepository universities, CurrentActor actor, Clock clock) {
        this.universities = universities; this.actor = actor; this.clock = clock;
    }
    @PreAuthorize("isAuthenticated() and (!#includeInactive or hasRole('ADMIN'))")
    public PageResponse<UniversitySummary> list(@Size(max = 200) String query, @Min(0) int page,
                                               @Min(1) @Max(100) int size, boolean includeInactive) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return PageResponse.from(universities.search(pattern, includeInactive,
                PageRequest.of(page, size, Sort.by("id").ascending())).map(UniversitySummary::from));
    }
    @PreAuthorize("isAuthenticated()")
    public UniversityResponse get(@Positive Long id) {
        University u = universities.findById(id).orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        if (!u.isActive() && !actor.isAdmin()) throw new ResourceNotFoundException("Üniversite bulunamadı.");
        return UniversityResponse.from(u);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public UniversityResponse create(@Valid CreateUniversityRequest request) {
        if (universities.existsByCode(request.code())) throw new ResourceConflictException("Üniversite kodu zaten kullanılıyor.");
        return UniversityResponse.from(universities.saveAndFlush(new University(request.code(), request.name(),
                request.city(), request.countryCode(), request.websiteUrl(), clock.instant())));
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public UniversityResponse update(@Positive Long id, @Valid UpdateUniversityRequest request) {
        University u = universities.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        if (!Objects.equals(u.getVersion(), request.version())) throw new ResourceConflictException("Kayıt değişti; güncel sürümü alınız.");
        u.update(request.name() == null ? u.getName() : request.name(),
                request.city() == null ? u.getCity() : request.city(),
                request.websiteUrl() == null ? u.getWebsiteUrl() : (request.websiteUrl().isBlank() ? null : request.websiteUrl()),
                request.active() == null ? u.isActive() : request.active(), clock.instant());
        return UniversityResponse.from(universities.saveAndFlush(u));
    }
}
