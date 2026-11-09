package com.campusplus.backend.messaging;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.messaging.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectMessageWorkspaceTest {
    UserRepository users; StudentProfileRepository profiles; UniversityRepository universities;
    UserBlockRepository blocks; DirectConversationRepository conversations; CurrentActor current; EntityManager em;
    DirectMessageWorkspace workspace; User first, second; University university; DirectConversation conversation;
    @BeforeEach void setup() {
        users = mock(UserRepository.class); profiles = mock(StudentProfileRepository.class); universities = mock(UniversityRepository.class);
        blocks = mock(UserBlockRepository.class); conversations = mock(DirectConversationRepository.class);
        current = mock(CurrentActor.class); em = mock(EntityManager.class); when(current.userId()).thenReturn(2L);
        workspace = new DirectMessageWorkspace(users, profiles, universities, blocks, conversations, current, new DirectMessagePolicy(), em);
        first = user(1); second = user(2);
        university = new University("UNI", "University", "City", "TR", null, Instant.EPOCH); ReflectionTestUtils.setField(university, "id", 10L);
        conversation = new DirectConversation(first, second, Instant.EPOCH); ReflectionTestUtils.setField(conversation, "id", 20L);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(first)); when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(profiles.findById(1L)).thenReturn(Optional.of(new StudentProfile(first, university, "Engineering", null, null, Instant.EPOCH)));
        when(profiles.findById(2L)).thenReturn(Optional.of(new StudentProfile(second, university, "Engineering", null, null, Instant.EPOCH)));
        when(universities.findByIdForShare(10L)).thenReturn(Optional.of(university));
        when(conversations.findById(20L)).thenReturn(Optional.of(conversation));
    }
    User user(long id) { var u = new User("Student", "student@example.test"); ReflectionTestUtils.setField(u, "id", id); return u; }
    @AfterEach void clear() { TransactionSynchronizationManager.clear(); }
    @Test void allWritersLockUsersAscendingThenConversationThenUniversityAndRefreshState() {
        var pair = workspace.lockPair(1); workspace.lockConversation(20); workspace.requireWritable(pair);
        var order = inOrder(users, conversations, universities, em);
        order.verify(users).findByIdForUpdate(1L); order.verify(em).refresh(first);
        order.verify(users).findByIdForUpdate(2L); order.verify(em).refresh(second);
        order.verify(em).refresh(conversation, LockModeType.PESSIMISTIC_WRITE);
        order.verify(universities).findByIdForShare(10L); order.verify(em).refresh(university, LockModeType.PESSIMISTIC_READ);
    }
    @Test void changeWhileWaitingForLockIsRechecked() {
        doAnswer(i -> { second.revokeRole(Role.STUDENT); return null; }).when(em).refresh(second);
        var pair = workspace.lockPair(1); assertThrows(ResourceNotFoundException.class, () -> workspace.requireWritable(pair));
    }
    @Test void newlyBlockedRecipientPreventsSendAfterLocks() {
        var pair = workspace.lockPair(1);
        when(blocks.existsByBlockerIdAndBlockedIdAndActiveTrue(1, 2)).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> workspace.requireWritable(pair));
    }
    @Test void universityDeactivationWhileWaitingIsRechecked() {
        doAnswer(i -> { ReflectionTestUtils.setField(university, "active", false); return null; })
                .when(em).refresh(university, LockModeType.PESSIMISTIC_READ);
        var pair = workspace.lockPair(1); assertThrows(ResourceNotFoundException.class, () -> workspace.requireWritable(pair));
    }
    @Test void thirdPartyCannotReadOrObtainConversationLock() {
        when(current.userId()).thenReturn(3L);
        assertThrows(ResourceNotFoundException.class, () -> workspace.readable(20));
        assertThrows(ResourceNotFoundException.class, () -> workspace.lockConversation(20));
        verify(em, never()).refresh(any(),eq(LockModeType.PESSIMISTIC_WRITE));
    }
    @Test void selfAndMissingRecipientsHaveSameGenericError() {
        var self = assertThrows(ResourceNotFoundException.class, () -> workspace.lockPair(2));
        var missing = assertThrows(ResourceNotFoundException.class, () -> workspace.lockPair(4));
        assertEquals(self.getMessage(), missing.getMessage());
    }
    @Test void readOnlyTransactionCannotBeUsedForAnyWriter() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalTransactionStateException.class, () -> workspace.lockPair(1));
        assertThrows(IllegalTransactionStateException.class, () -> workspace.lockConversation(20));
        assertThrows(IllegalTransactionStateException.class, () -> workspace.requireWritable(new DirectMessageWorkspace.Pair(conversation.members(), second, first)));
        verifyNoInteractions(users, conversations, profiles, universities, blocks);
    }
    @Test void workspaceProxyRequiresExistingTransactionEvenForReads() {
        var source = mock(DataSource.class); var manager = new DataSourceTransactionManager(source);
        var factory = new ProxyFactory(workspace);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var proxy = (DirectMessageWorkspace)factory.getProxy();
        assertThrows(IllegalTransactionStateException.class, () -> proxy.lockPair(1));
        assertThrows(IllegalTransactionStateException.class, () -> proxy.readable(20));
        verifyNoInteractions(source);
    }
    @Test void stalePreflightIsRefreshedDirectlyUnderWriteLockBeforeItsVersionIsUsed() {
        ReflectionTestUtils.setField(conversation,"version",0L);
        assertSame(conversation,workspace.readable(20));
        doAnswer(i -> {
            ReflectionTestUtils.setField(conversation,"version",1L);
            conversation.nextMessage(Instant.EPOCH); return null;
        }).when(em).refresh(conversation,LockModeType.PESSIMISTIC_WRITE);
        assertEquals(1,workspace.lockConversation(20).getLastMessageNumber());
        assertEquals(1L,ReflectionTestUtils.getField(conversation,"version"));
    }
}
