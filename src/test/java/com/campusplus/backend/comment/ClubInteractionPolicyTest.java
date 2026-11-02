package com.campusplus.backend.comment;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import com.campusplus.backend.security.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class ClubInteractionPolicyTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private final ClubInteractionPolicy policy = new ClubInteractionPolicy(new ClubPolicy());
    private User actor, other;
    private University university, foreign;
    private Club club, otherClub;
    private StudentProfile profile, foreignProfile;
    private ClubMembership member;

    void field(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
    @BeforeEach void setup() {
        actor = new User("Student", "student@example.test"); field(actor, "id", 1L);
        other = new User("Other", "other@example.test"); field(other, "id", 2L);
        university = new University("UNI", "University", "City", "TR", null, NOW); field(university, "id", 10L);
        foreign = new University("OTHER", "Other", "City", "TR", null, NOW); field(foreign, "id", 20L);
        profile = new StudentProfile(actor, university, "Department", null, null, NOW);
        foreignProfile = new StudentProfile(actor, foreign, "Department", null, null, NOW);
        club = new Club(university, actor, "Club", "club", "Description", null, NOW); field(club, "id", 100L); field(club, "status", ClubStatus.ACTIVE);
        otherClub = new Club(university, actor, "Other", "other", "Description", null, NOW); field(otherClub, "id", 200L); field(otherClub, "status", ClubStatus.ACTIVE);
        member = new ClubMembership(club, profile, false, NOW); field(member, "status", ClubMembershipStatus.ACTIVE);
    }
    Event event(EventAudience audience) {
        var e = new Event(club, actor, "Event", "Description", "Hall", null, audience, 10,
                NOW, NOW.plusSeconds(300), NOW.plusSeconds(600), NOW.plusSeconds(1200), "Europe/Istanbul", NOW);
        e.publish(NOW); return e;
    }
    Announcement announcement(AnnouncementAudience audience) {
        var a = new Announcement(club, actor, "Announcement", "Body", audience, null, NOW); a.publish(NOW); return a;
    }
    @ParameterizedTest @EnumSource(EventStatus.class)
    void eventStateMatrixDoesNotTreatAdministrativeDraftAsPublished(EventStatus status) {
        var e = event(EventAudience.UNIVERSITY_STUDENTS); field(e, "status", status);
        actor.grantRole(Role.ADMIN); actor.grantRole(Role.CLUB_ADMIN); field(member, "role", ClubMembershipRole.OWNER);
        assertEquals(status != EventStatus.DRAFT, policy.readable(e, actor, member, profile));
        assertEquals(status == EventStatus.PUBLISHED || status == EventStatus.COMPLETED, policy.writable(e, actor, member, profile));
    }
    @ParameterizedTest @EnumSource(AnnouncementStatus.class)
    void announcementStateMatrixHasNoManagerOrAdminBypass(AnnouncementStatus status) {
        var a = announcement(AnnouncementAudience.UNIVERSITY_STUDENTS); field(a, "status", status);
        actor.grantRole(Role.ADMIN); actor.grantRole(Role.CLUB_ADMIN); field(member, "role", ClubMembershipRole.OWNER);
        assertEquals(status == AnnouncementStatus.PUBLISHED, policy.readable(a, actor, member, profile));
        assertEquals(status == AnnouncementStatus.PUBLISHED, policy.writable(a, actor, member, profile));
    }
    @Test void publicEventReadIsBroaderThanSocialWriteAndAnnouncementRead() {
        var e = event(EventAudience.UNIVERSITY_STUDENTS); var a = announcement(AnnouncementAudience.UNIVERSITY_STUDENTS);
        assertTrue(policy.readable(e, actor, null, foreignProfile)); assertTrue(policy.readable(e, actor, null, null));
        assertFalse(policy.writable(e, actor, null, foreignProfile)); assertFalse(policy.writable(e, actor, null, null));
        assertFalse(policy.readable(a, actor, null, foreignProfile)); assertFalse(policy.readable(a, actor, null, null));
        assertTrue(policy.writable(e, actor, null, profile)); assertTrue(policy.writable(a, actor, null, profile));
    }
    @ParameterizedTest @EnumSource(ClubMembershipStatus.class)
    void privateAudiencesRequireCurrentActiveMembership(ClubMembershipStatus status) {
        field(member, "status", status);
        var e = event(EventAudience.CLUB_MEMBERS); var a = announcement(AnnouncementAudience.CLUB_MEMBERS);
        assertEquals(status == ClubMembershipStatus.ACTIVE, policy.readable(e, actor, member, profile));
        assertEquals(status == ClubMembershipStatus.ACTIVE, policy.writable(e, actor, member, profile));
        assertEquals(status == ClubMembershipStatus.ACTIVE, policy.readable(a, actor, member, profile));
        assertEquals(status == ClubMembershipStatus.ACTIVE, policy.writable(a, actor, member, profile));
    }
    @Test void membershipAndProfileCannotBelongToAnotherActorOrClub() {
        var e = event(EventAudience.CLUB_MEMBERS); var a = announcement(AnnouncementAudience.CLUB_MEMBERS);
        var otherPerson = new StudentProfile(other, university, "Department", null, null, NOW);
        var othersMember = new ClubMembership(club, otherPerson, false, NOW); field(othersMember, "status", ClubMembershipStatus.ACTIVE);
        var wrongClub = new ClubMembership(otherClub, profile, false, NOW); field(wrongClub, "status", ClubMembershipStatus.ACTIVE);
        assertFalse(policy.readable(e, actor, othersMember, profile)); assertFalse(policy.readable(a, actor, othersMember, profile));
        assertFalse(policy.readable(e, actor, wrongClub, profile)); assertFalse(policy.readable(a, actor, wrongClub, profile));
        assertFalse(policy.readable(e, actor, member, otherPerson)); assertFalse(policy.readable(a, actor, member, otherPerson));
        assertFalse(policy.readable(e, actor, member, foreignProfile)); assertFalse(policy.readable(a, actor, member, foreignProfile));
    }
    @ParameterizedTest @EnumSource(ClubStatus.class)
    void inactiveSourceContextClosesNormalInteractionEvenForAdmin(ClubStatus status) {
        field(club, "status", status); actor.grantRole(Role.ADMIN);
        assertEquals(status == ClubStatus.ACTIVE, policy.readable(event(EventAudience.UNIVERSITY_STUDENTS), actor, member, profile));
        assertEquals(status == ClubStatus.ACTIVE, policy.readable(announcement(AnnouncementAudience.UNIVERSITY_STUDENTS), actor, member, profile));
    }
    @Test void inactiveUniversityArchiveAndMissingPublishTimestampAreClosed() {
        var e = event(EventAudience.UNIVERSITY_STUDENTS); var a = announcement(AnnouncementAudience.UNIVERSITY_STUDENTS);
        field(university, "active", false); assertFalse(policy.readable(e, actor, member, profile)); assertFalse(policy.readable(a, actor, member, profile));
        field(university, "active", true); field(e, "archivedAt", NOW); field(a, "archivedAt", NOW);
        assertFalse(policy.readable(e, actor, member, profile)); assertFalse(policy.readable(a, actor, member, profile));
        field(e, "archivedAt", null); field(a, "archivedAt", null); field(e, "publishedAt", null); field(a, "publishedAt", null);
        assertFalse(policy.readable(e, actor, member, profile)); assertFalse(policy.readable(a, actor, member, profile));
    }
    @Test void adminOrMarkerWithoutStudentRoleDoesNotGrantParticipation() {
        actor.revokeRole(Role.STUDENT); actor.grantRole(Role.ADMIN); actor.grantRole(Role.CLUB_ADMIN);
        assertFalse(policy.writable(event(EventAudience.UNIVERSITY_STUDENTS), actor, member, profile));
        assertFalse(policy.writable(announcement(AnnouncementAudience.UNIVERSITY_STUDENTS), actor, member, profile));
    }
    @ParameterizedTest @EnumSource(ClubMembershipRole.class)
    void localModerationRequiresCorrectManagementRoleAndMarker(ClubMembershipRole role) {
        var e = event(EventAudience.UNIVERSITY_STUDENTS); var a = announcement(AnnouncementAudience.UNIVERSITY_STUDENTS);
        field(member, "role", role); actor.grantRole(Role.ADMIN);
        assertFalse(policy.resourceManager(e, actor, member, profile)); assertFalse(policy.resourceManager(a, actor, member, profile));
        actor.grantRole(Role.CLUB_ADMIN);
        assertEquals(role != ClubMembershipRole.MEMBER, policy.resourceManager(e, actor, member, profile));
        assertEquals(role != ClubMembershipRole.MEMBER, policy.resourceManager(a, actor, member, profile));
        assertFalse(policy.resourceManager(e, actor, member, foreignProfile));
    }
    @Test void cancelledEventKeepsLocalModerationButNotSocialWrite() {
        var e = event(EventAudience.UNIVERSITY_STUDENTS); field(e, "status", EventStatus.CANCELLED);
        actor.grantRole(Role.CLUB_ADMIN); field(member, "role", ClubMembershipRole.MANAGER);
        assertTrue(policy.resourceManager(e, actor, member, profile)); assertFalse(policy.writable(e, actor, member, profile));
        field(member, "status", ClubMembershipStatus.LEFT); assertFalse(policy.resourceManager(e, actor, member, profile));
    }
    @Test void invisibleSourceHasSafe404AndNullActorDoesNotGetReadPermission() {
        var e = event(EventAudience.UNIVERSITY_STUDENTS); var a = announcement(AnnouncementAudience.UNIVERSITY_STUDENTS);
        field(e, "archivedAt", NOW); field(a, "archivedAt", NOW);
        assertThrows(ResourceNotFoundException.class, () -> policy.requireReadable(e, actor, member, profile));
        assertThrows(ResourceNotFoundException.class, () -> policy.requireReadable(a, actor, member, profile));
        assertFalse(policy.readable(event(EventAudience.UNIVERSITY_STUDENTS), null, null, null));
        assertFalse(policy.readable(announcement(AnnouncementAudience.UNIVERSITY_STUDENTS), null, null, null));
    }
}
