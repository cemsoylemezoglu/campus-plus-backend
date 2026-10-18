package com.campusplus.backend.service;

import com.campusplus.backend.dto.academic.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly = true)
public class StudentProfileService {
    private final StudentProfileRepository profiles;
    private final UserRepository users;
    private final UniversityRepository universities;
    private final CurrentActor actor;
    private final Clock clock;
    public StudentProfileService(StudentProfileRepository profiles, UserRepository users,
                                 UniversityRepository universities, CurrentActor actor, Clock clock) {
        this.profiles = profiles; this.users = users; this.universities = universities;
        this.actor = actor; this.clock = clock;
    }
    @PreAuthorize("hasAnyRole('STUDENT','CLUB_ADMIN','ADMIN')")
    public StudentProfileResponse getOwn() { return response(actor.userId()); }

    @PreAuthorize("hasRole('ADMIN')")
    public StudentProfileResponse getForAdmin(@Positive Long userId) { return response(userId); }

    @Transactional @PreAuthorize("hasAnyRole('STUDENT','CLUB_ADMIN','ADMIN')")
    public StudentProfileResponse createOwn(@Valid CreateStudentProfileRequest request) {
        long id = actor.userId();
        User user = lockUser(id);
        if (profiles.existsById(id)) throw new ResourceConflictException("Öğrenci profili zaten mevcut.");
        University university = lockedUniversity(request.universityId());
        if (!university.isActive()) throw new ResourceConflictException("Pasif üniversitede profil oluşturulamaz.");
        return StudentProfileResponse.from(profiles.saveAndFlush(new StudentProfile(user, university,
                request.department(), request.studyYear(), request.bio(), clock.instant())));
    }
    @Transactional @PreAuthorize("hasAnyRole('STUDENT','CLUB_ADMIN','ADMIN')")
    public StudentProfileResponse updateOwn(@Valid UpdateStudentProfileRequest request) {
        return update(actor.userId(), request);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public StudentProfileResponse updateForAdmin(@Positive Long userId, @Valid UpdateStudentProfileRequest request) {
        return update(userId, request);
    }
    private StudentProfileResponse response(Long id) {
        return StudentProfileResponse.from(profiles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Öğrenci profili bulunamadı.")));
    }
    private StudentProfileResponse update(Long id, UpdateStudentProfileRequest request) {
        lockUser(id);
        StudentProfile profile = profiles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Öğrenci profili bulunamadı."));
        if (!Objects.equals(profile.getVersion(), request.version())) throw new ResourceConflictException("Kayıt değişti; güncel sürümü alınız.");
        University university = lockedUniversity(request.universityId());
        if (!university.getId().equals(profile.getUniversity().getId()) && !university.isActive()) {
            throw new ResourceConflictException("Pasif üniversiteye geçiş yapılamaz.");
        }
        // Phase 1 has no membership module. ClubMembership implementation must add its
        // blocking-status check here under the SAME user lock before enabling memberships.
        profile.update(university, request.department(), request.studyYear(), request.bio(), clock.instant());
        return StudentProfileResponse.from(profiles.saveAndFlush(profile));
    }
    private User lockUser(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
    }
    private University lockedUniversity(Long id) {
        return universities.findByIdForShare(id).orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
    }
}
