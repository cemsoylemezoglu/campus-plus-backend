package com.campusplus.backend.comment;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.clubinteraction.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Simulates changes while waiting for locks. Actual PostgreSQL lock races remain a separate integration step. */
class ClubInteractionWorkspaceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private UserRepository users;
    private ClubRepository clubs;
    private StudentProfileRepository profiles;
    private ClubMembershipRepository memberships;
    private UniversityRepository universities;
    private EventRepository events;
    private AnnouncementRepository announcements;
    private EntityManager em;
    private ClubInteractionWorkspace workspace;
    private User actor;
    private University university;
    private Club club;
    private StudentProfile profile;
    private ClubMembership membership;
    private Event event;
    private Announcement announcement;

    private static void field(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
    private ClubSourceRef ref(ClubSourceKind kind) { return new ClubSourceRef(kind, 7); }
    private Object source(ClubSourceKind kind) { return kind == ClubSourceKind.EVENT ? event : announcement; }

    @BeforeEach void setup() {
        users = mock(UserRepository.class); clubs = mock(ClubRepository.class); profiles = mock(StudentProfileRepository.class);
        memberships = mock(ClubMembershipRepository.class); universities = mock(UniversityRepository.class);
        events = mock(EventRepository.class); announcements = mock(AnnouncementRepository.class); em = mock(EntityManager.class);
        var current = mock(CurrentActor.class); when(current.userId()).thenReturn(1L);
        var clubPolicy = new ClubPolicy();
        var core = new ClubWorkspace(clubs, memberships, profiles, universities, users, current,
                clubPolicy, new ClubMembershipPolicy(), Clock.fixed(NOW, ZoneOffset.UTC), em);
        workspace = new ClubInteractionWorkspace(core, events, announcements, new ClubInteractionPolicy(clubPolicy), em);
        actor = new User("Student", "student@example.test"); field(actor, "id", 1L);
        university = new University("UNI", "University", "City", "TR", null, NOW); field(university, "id", 10L);
        profile = new StudentProfile(actor, university, "Engineering", null, null, NOW);
        club = new Club(university, actor, "Club", "club", "Description", null, NOW); field(club, "id", 100L); field(club, "status", ClubStatus.ACTIVE);
        membership = new ClubMembership(club, profile, false, NOW); field(membership, "status", ClubMembershipStatus.ACTIVE);
        event = new Event(club, actor, "Private event title", "Private event body", "Hall", null, EventAudience.UNIVERSITY_STUDENTS, 10,
                NOW, NOW.plusSeconds(300), NOW.plusSeconds(600), NOW.plusSeconds(1200), "Europe/Istanbul", NOW);
        field(event, "id", 7L); event.publish(NOW);
        announcement = new Announcement(club, actor, "Private announcement title", "Private announcement body", AnnouncementAudience.UNIVERSITY_STUDENTS, event, NOW);
        field(announcement, "id", 7L); announcement.publish(NOW);
        when(users.findById(1L)).thenReturn(Optional.of(actor)); when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(actor));
        when(clubs.findByIdForShare(100L)).thenReturn(Optional.of(club)); when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        when(universities.findByIdForShare(10L)).thenReturn(Optional.of(university));
        when(memberships.findByClubIdAndUserId(100L, 1L)).thenReturn(Optional.of(membership));
        when(events.findById(7L)).thenReturn(Optional.of(event)); when(events.findForUpdate(7L, 100L)).thenReturn(Optional.of(event));
        when(announcements.findById(7L)).thenReturn(Optional.of(announcement)); when(announcements.findForUpdate(7L, 100L)).thenReturn(Optional.of(announcement));
    }

    @AfterEach void clearTransactionFlags() { TransactionSynchronizationManager.clear(); }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void readDispatchPreservesNamespacesAndDoesNotExposeContentInLogs(ClubSourceKind kind) {
        var context = workspace.readable(ref(kind));
        assertEquals(ref(kind), context.source().ref()); assertSame(actor, context.actor());
        assertFalse(context.toString().contains("student@example"));
        assertFalse(context.source().toString().contains("Private"));
        if (kind == ClubSourceKind.EVENT) verifyNoInteractions(announcements); else verifyNoInteractions(events);
        verify(users, never()).findByIdForUpdate(anyLong());
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void participantLocksInExistingSourceWriterOrderWithoutLockingLinkedEvent(ClubSourceKind kind) {
        var context = workspace.participant(ref(kind));
        assertEquals(ref(kind), context.source().ref());
        var order = inOrder(users, clubs, events, announcements, universities, em);
        order.verify(users).findByIdForUpdate(1L); order.verify(em).refresh(actor);
        order.verify(clubs).findByIdForShare(100L); order.verify(em).refresh(club, LockModeType.PESSIMISTIC_READ);
        if (kind == ClubSourceKind.EVENT) order.verify(events).findForUpdate(7L, 100L);
        else order.verify(announcements).findForUpdate(7L, 100L);
        order.verify(em).refresh(source(kind), LockModeType.PESSIMISTIC_WRITE);
        order.verify(universities).findByIdForShare(10L); order.verify(em).refresh(university, LockModeType.PESSIMISTIC_READ);
        if (kind == ClubSourceKind.ANNOUNCEMENT) verifyNoInteractions(events);
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void lifecycleChangeWhileWaitingForSourceLockIsRechecked(ClubSourceKind kind) {
        doAnswer(invocation -> {
            if (kind == ClubSourceKind.EVENT) field(event, "status", EventStatus.CANCELLED);
            else field(announcement, "status", AnnouncementStatus.WITHDRAWN);
            return null;
        }).when(em).refresh(source(kind), LockModeType.PESSIMISTIC_WRITE);
        if (kind == ClubSourceKind.EVENT) assertThrows(AccessDeniedException.class, () -> workspace.participant(ref(kind)));
        else assertThrows(ResourceNotFoundException.class, () -> workspace.participant(ref(kind)));
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void privateMembershipLossWhileWaitingClosesAccess(ClubSourceKind kind) {
        field(event, "audience", EventAudience.CLUB_MEMBERS); field(announcement, "audience", AnnouncementAudience.CLUB_MEMBERS);
        doAnswer(invocation -> { field(membership, "status", ClubMembershipStatus.LEFT); return null; })
                .when(em).refresh(source(kind), LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ResourceNotFoundException.class, () -> workspace.participant(ref(kind)));
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void roleRevocationWhileWaitingCannotUsePreflightAuthority(ClubSourceKind kind) {
        actor.grantRole(Role.CLUB_ADMIN); field(membership, "role", ClubMembershipRole.MANAGER);
        doAnswer(invocation -> { actor.revokeRole(Role.CLUB_ADMIN); return null; }).when(em).refresh(actor);
        assertThrows(AccessDeniedException.class, () -> workspace.resourceManager(ref(kind)));
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void universityDeactivationWhileWaitingClosesAccess(ClubSourceKind kind) {
        doAnswer(invocation -> { field(university, "active", false); return null; })
                .when(em).refresh(university, LockModeType.PESSIMISTIC_READ);
        assertThrows(ResourceNotFoundException.class, () -> workspace.participant(ref(kind)));
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void changedSourceClubIsRejectedBeforeUniversityLookup(ClubSourceKind kind) {
        var other = new Club(university, actor, "Other", "other", "Description", null, NOW); field(other, "id", 200L);
        doAnswer(invocation -> { field(source(kind), "club", other); return null; })
                .when(em).refresh(source(kind), LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ResourceNotFoundException.class, () -> workspace.participant(ref(kind)));
        verifyNoInteractions(universities);
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void adminDoesNotBypassDraftAndDoesNotTakeWriteLocks(ClubSourceKind kind) {
        actor.grantRole(Role.ADMIN); field(event, "status", EventStatus.DRAFT); field(announcement, "status", AnnouncementStatus.DRAFT);
        assertThrows(ResourceNotFoundException.class, () -> workspace.resourceManager(ref(kind)));
        verify(users, never()).findByIdForUpdate(anyLong());
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void markerOrAdminAloneDoesNotAllowLocalModeration(ClubSourceKind kind) {
        actor.grantRole(Role.ADMIN); actor.grantRole(Role.CLUB_ADMIN);
        assertThrows(AccessDeniedException.class, () -> workspace.resourceManager(ref(kind)));
        verify(users, never()).findByIdForUpdate(anyLong());
    }

    @Test void cancelledEventSupportsManagementButNotNewInteraction() {
        field(event, "status", EventStatus.CANCELLED); actor.grantRole(Role.CLUB_ADMIN); field(membership, "role", ClubMembershipRole.OWNER);
        assertEquals(ref(ClubSourceKind.EVENT), workspace.resourceManager(ref(ClubSourceKind.EVENT)).source().ref());
        assertThrows(AccessDeniedException.class, () -> workspace.participant(ref(ClubSourceKind.EVENT)));
    }

    @Test void foreignPublicEventReaderCannotWriteAndCannotReadAnnouncement() {
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        assertNotNull(workspace.readable(ref(ClubSourceKind.EVENT)));
        assertThrows(AccessDeniedException.class, () -> workspace.participant(ref(ClubSourceKind.EVENT)));
        assertThrows(ResourceNotFoundException.class, () -> workspace.readable(ref(ClubSourceKind.ANNOUNCEMENT)));
        verify(users, never()).findByIdForUpdate(anyLong());
    }

    @Test void readOnlyTransactionCannotAcquireWriterLocks() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalTransactionStateException.class, () -> workspace.participant(ref(ClubSourceKind.EVENT)));
        verifyNoInteractions(events, announcements, users, clubs, universities);
    }

    @Test void springProxyRequiresCallerTransactionBeforeAnyDatabaseAccess() {
        var dataSource = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(dataSource);
        var factory = new ProxyFactory(workspace);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var proxy = (ClubInteractionWorkspace) factory.getProxy();
        assertThrows(IllegalTransactionStateException.class, () -> proxy.readable(ref(ClubSourceKind.EVENT)));
        assertThrows(IllegalTransactionStateException.class, () -> proxy.participant(ref(ClubSourceKind.EVENT)));
        assertThrows(IllegalTransactionStateException.class, () -> proxy.resourceManager(ref(ClubSourceKind.EVENT)));
        verifyNoInteractions(dataSource, events, announcements, users, clubs, universities);
    }
}
