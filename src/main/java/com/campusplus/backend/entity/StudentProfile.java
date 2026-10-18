package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {
    @Id @Column(name = "user_id")
    private Long userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;
    @Column(nullable = false, length = 150)
    private String department;
    @Column(name = "study_year")
    private Short studyYear;
    @Column(length = 1000)
    private String bio;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected StudentProfile() {}
    public StudentProfile(User user, University university, String department, Short studyYear, String bio, Instant now) {
        this.user = user; this.userId = user.getId(); this.university = university;
        this.department = department; this.studyYear = studyYear; this.bio = bio;
        this.createdAt = now; this.updatedAt = now;
    }
    public void update(University university, String department, Short studyYear, String bio, Instant now) {
        this.university = university; this.department = department; this.studyYear = studyYear;
        this.bio = bio; this.updatedAt = now;
    }
    public Long getUserId() { return userId; }
    public University getUniversity() { return university; }
    public String getDepartment() { return department; }
    public Short getStudyYear() { return studyYear; }
    public String getBio() { return bio; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
