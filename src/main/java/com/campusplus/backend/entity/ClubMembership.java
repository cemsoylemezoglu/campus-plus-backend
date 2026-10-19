package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "club_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "user_id"}))
public class ClubMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "club_id", nullable = false, updatable = false)
    private Club club;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private StudentProfile studentProfile;
    @Column(name = "user_id", insertable = false, updatable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ClubMembershipRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ClubMembershipStatus status;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "joined_at") private Instant joinedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private User reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected ClubMembership() {}
    public ClubMembership(Club club, StudentProfile studentProfile, boolean founder, Instant now) {
        this.club = club; this.studentProfile = studentProfile; this.userId = studentProfile.getUserId();
        this.role = founder ? ClubMembershipRole.OWNER : ClubMembershipRole.MEMBER;
        this.status = founder ? ClubMembershipStatus.ACTIVE : ClubMembershipStatus.PENDING;
        this.requestedAt = now; this.createdAt = now; this.updatedAt = now;
        if (founder) this.joinedAt = now;
    }
    public void requestAgain(Instant now) {
        status = ClubMembershipStatus.PENDING; role = ClubMembershipRole.MEMBER;
        requestedAt = now; endedAt = null; reviewedAt = null; reviewedBy = null; decisionReason = null; updatedAt = now;
    }
    public void transition(ClubMembershipStatus next, User reviewer, String reason, Instant now) {
        status = next; reviewedBy = reviewer; reviewedAt = now; decisionReason = reason; updatedAt = now;
        if (next == ClubMembershipStatus.ACTIVE) { joinedAt = now; endedAt = null; }
        else { role = ClubMembershipRole.MEMBER; endedAt = now; }
    }
    public void changeRole(ClubMembershipRole role, Instant now) { this.role = role; this.updatedAt = now; }
    public Long getId() { return id; }
    public Club getClub() { return club; }
    public Long getUserId() { return userId; }
    public StudentProfile getStudentProfile() { return studentProfile; }
    public ClubMembershipRole getRole() { return role; }
    public ClubMembershipStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
