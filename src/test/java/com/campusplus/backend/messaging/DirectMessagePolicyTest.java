package com.campusplus.backend.messaging;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.security.DirectMessagePolicy;
import com.campusplus.backend.security.DirectMessagePolicy.ContactState;
import com.campusplus.backend.service.messaging.DirectConversationMembers;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class DirectMessagePolicyTest {
    private final DirectMessagePolicy policy = new DirectMessagePolicy();
    private final DirectConversationMembers members = DirectConversationMembers.between(1, 2);
    private User actor, recipient, outsider;
    private University university;
    private StudentProfile actorProfile, recipientProfile;

    @BeforeEach void setup() {
        actor = user(1); recipient = user(2); outsider = user(3);
        university = university(10);
        actorProfile = profile(actor, university); recipientProfile = profile(recipient, university);
    }

    private User user(long id) {
        var user = new User("Student", "student" + id + "@example.test");
        ReflectionTestUtils.setField(user, "id", id); return user;
    }
    private University university(long id) {
        var uni = new University("UNI" + id, "University", "City", "TR", null, Instant.EPOCH);
        ReflectionTestUtils.setField(uni, "id", id); return uni;
    }
    private StudentProfile profile(User user, University uni) {
        return new StudentProfile(user, uni, "Department", null, null, Instant.EPOCH);
    }
    private boolean writable(ContactState contact) {
        return policy.writable(members, actor, recipient, actorProfile, recipientProfile, contact);
    }

    @Test void bothParticipantsMayReadAndSendInEitherDirection() {
        assertTrue(policy.readable(members, actor)); assertTrue(policy.readable(members, recipient));
        assertTrue(writable(ContactState.CLEAR));
        assertTrue(policy.writable(members, recipient, actor, recipientProfile, actorProfile, ContactState.CLEAR));
    }
    @ParameterizedTest @EnumSource(Role.class)
    void noGlobalRoleAllowsReadingSomeoneElsesConversation(Role role) {
        outsider.grantRole(role); assertFalse(policy.readable(members, outsider));
        assertFalse(policy.writable(members, outsider, recipient, profile(outsider, university), recipientProfile, ContactState.CLEAR));
    }
    @ParameterizedTest @EnumSource(ContactState.class)
    void eitherDirectionOfBlockingStopsSendingButPreservesHistory(ContactState contact) {
        assertEquals(contact == ContactState.CLEAR, writable(contact));
        assertTrue(policy.readable(members, actor)); assertTrue(policy.readable(members, recipient));
    }
    @Test void missingBlockLookupFailsClosed() { assertFalse(writable(null)); }
    @Test void profilesMustMatchBothActualParticipants() {
        actorProfile = profile(recipient, university); assertFalse(writable(ContactState.CLEAR));
        actorProfile = profile(actor, university); recipientProfile = profile(actor, university);
        assertFalse(writable(ContactState.CLEAR));
    }
    @Test void missingProfilesAndDifferentUniversitiesStopSending() {
        actorProfile = null; assertFalse(writable(ContactState.CLEAR));
        actorProfile = profile(actor, university); recipientProfile = null; assertFalse(writable(ContactState.CLEAR));
        recipientProfile = profile(recipient, university(20)); assertFalse(writable(ContactState.CLEAR));
        assertTrue(policy.readable(members, actor));
    }
    @Test void inactiveUniversitiesStopSendingOnBothSides() {
        ReflectionTestUtils.setField(university, "active", false); assertFalse(writable(ContactState.CLEAR));
        assertTrue(policy.readable(members, actor));
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CLUB_ADMIN"})
    void managementRoleDoesNotReplaceStudentEligibility(Role role) {
        actor.revokeRole(Role.STUDENT); actor.grantRole(role); assertFalse(writable(ContactState.CLEAR));
        actor.grantRole(Role.STUDENT); recipient.revokeRole(Role.STUDENT); recipient.grantRole(role);
        assertFalse(writable(ContactState.CLEAR)); assertTrue(policy.readable(members, recipient));
    }
    @Test void recipientCannotBeSelfOrUnrelatedUser() {
        assertFalse(policy.writable(members, actor, actor, actorProfile, actorProfile, ContactState.CLEAR));
        assertFalse(policy.writable(members, actor, outsider, actorProfile, profile(outsider, university), ContactState.CLEAR));
    }
    @Test void absentOrTransientIdentitiesDoNotGrantAccess() {
        assertFalse(policy.readable(null, actor)); assertFalse(policy.readable(members, null));
        assertFalse(policy.readable(members, new User("Transient", "transient@example.test")));
        assertFalse(policy.writable(members, actor, null, actorProfile, recipientProfile, ContactState.CLEAR));
    }
    @Test void conversationIdentityIsCanonicalAndRejectsInvalidPairs() {
        assertEquals(members, DirectConversationMembers.between(2, 1));
        assertThrows(IllegalArgumentException.class, () -> DirectConversationMembers.between(1, 1));
        assertThrows(IllegalArgumentException.class, () -> DirectConversationMembers.between(0, 1));
        assertThrows(IllegalArgumentException.class, () -> DirectConversationMembers.between(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> new DirectConversationMembers(2, 1));
        assertFalse(members.contains(null)); assertFalse(members.contains(0L));
        assertTrue(DirectConversationMembers.between(1, Long.MAX_VALUE).contains(Long.MAX_VALUE));
    }
}
