package com.campusplus.backend.messaging;

import com.campusplus.backend.dto.messaging.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.CurrentActor;
import com.campusplus.backend.service.messaging.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Persistence mocks verify orchestration, not SQL constraints/rollback or real lock races. */
class DirectMessageServiceTest {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    DirectMessageWorkspace workspace; DirectConversationRepository conversations; DirectMessageRepository messages;
    DirectConversationStateRepository states; UserBlockRepository blocks; CurrentActor current;
    DirectMessageService service; User actor, recipient; DirectConversation conversation; DirectMessageWorkspace.Pair pair;
    @BeforeEach void setup() {
        workspace = mock(DirectMessageWorkspace.class); conversations = mock(DirectConversationRepository.class);
        messages = mock(DirectMessageRepository.class); states = mock(DirectConversationStateRepository.class);
        blocks = mock(UserBlockRepository.class); current = mock(CurrentActor.class); when(current.userId()).thenReturn(2L);
        actor = user(2); recipient = user(1); conversation = new DirectConversation(actor, recipient, NOW);
        ReflectionTestUtils.setField(conversation, "id", 10L);
        pair = new DirectMessageWorkspace.Pair(conversation.members(), actor, recipient);
        when(workspace.readable(10)).thenReturn(conversation); when(workspace.lockPair(1)).thenReturn(pair);
        when(workspace.lockConversation(10)).thenReturn(conversation);
        when(messages.saveAndFlush(any())).thenAnswer(i -> { DirectMessage m = i.getArgument(0); ReflectionTestUtils.setField(m, "id", 100L); return m; });
        service = new DirectMessageService(workspace, conversations, messages, states, blocks, current, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    User user(long id) { var u = new User("Student", "student@example.test"); ReflectionTestUtils.setField(u, "id", id); return u; }
    SendDirectMessageRequest request(String body) { return new SendDirectMessageRequest(UUID.randomUUID(), body); }
    @Test void sendUsesLockedPairBeforeConversationAndEligibilityBeforeWriting() {
        var result = service.send(10, request("hello")); assertEquals(2, result.senderId()); assertEquals(1, result.messageNumber());
        var order = inOrder(workspace, messages);
        order.verify(workspace).readable(10); order.verify(workspace).lockPair(1);
        order.verify(workspace).lockConversation(10); order.verify(workspace).requireWritable(pair);
        order.verify(messages).findByConversationIdAndSenderIdAndClientMessageId(eq(10L), eq(2L), any());
        order.verify(messages).saveAndFlush(any());
    }
    @Test void identicalRetryReturnsSameMessageWithoutIncrementingCounter() {
        var request = request("hello"); var old = new DirectMessage(conversation, actor, request.clientMessageId(), request.body(), NOW);
        ReflectionTestUtils.setField(old, "id", 100L);
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(10, 2, request.clientMessageId())).thenReturn(Optional.of(old));
        assertEquals(100, service.send(10, request).id()); assertEquals(1, conversation.getLastMessageNumber());
        verify(messages, never()).saveAndFlush(any()); verify(workspace).requireWritable(pair);
    }
    @Test void changedBodyWithSameKeyIs409() {
        var request = request("changed"); var old = new DirectMessage(conversation, actor, request.clientMessageId(), "original", NOW);
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(10, 2, request.clientMessageId())).thenReturn(Optional.of(old));
        assertThrows(ResourceConflictException.class, () -> service.send(10, request)); verify(messages, never()).saveAndFlush(any());
    }
    @Test void blockOrEligibilityChangeRejectsEvenPreviouslyUsedRetryKey() {
        doThrow(new ResourceNotFoundException("Unavailable")).when(workspace).requireWritable(pair);
        assertThrows(ResourceNotFoundException.class, () -> service.send(10, request("text"))); verifyNoInteractions(messages);
    }
    @Test void newConversationCreatesExactlyTwoStatesAndFirstMessage() {
        when(conversations.saveAndFlush(any())).thenReturn(conversation);
        var result = service.start(new StartDirectConversationRequest(1L, request("first")));
        assertEquals(10L, result.conversationId()); verify(states, times(2)).save(any()); verify(messages).saveAndFlush(any());
    }
    @Test void existingConversationIsReusedWithoutDuplicateStates() {
        when(conversations.findPair(1, 2)).thenReturn(Optional.of(conversation));
        service.start(new StartDirectConversationRequest(1L, request("next")));
        verify(conversations, never()).saveAndFlush(any()); verifyNoInteractions(states); verify(workspace).lockConversation(10);
    }
    @Test void readStateOnlyAdvancesWithinAnExistingConversation() {
        new DirectMessage(conversation, actor, UUID.randomUUID(), "a", NOW);
        var state = new DirectConversationState(conversation, 2, NOW);
        when(states.findByConversationIdAndUserId(10, 2)).thenReturn(Optional.of(state));
        when(messages.existsByConversationIdAndMessageNumber(10, 1)).thenReturn(true);
        assertEquals(1, service.markRead(10, new ReadDirectConversationRequest(1L)).throughMessageNumber());
        verify(workspace, never()).lockPair(anyLong());
        assertThrows(InvalidRequestException.class, () -> service.markRead(10, new ReadDirectConversationRequest(2L)));
    }
    @Test void missingReadMessageIsRejectedEvenIfCounterIsHigher() {
        new DirectMessage(conversation, actor, UUID.randomUUID(), "a", NOW);
        assertThrows(InvalidRequestException.class, () -> service.markRead(10, new ReadDirectConversationRequest(1L)));
        verifyNoInteractions(states);
    }
    @Test void blockingCreatesOnlyOwnDirectionAndUnblockingMissingPairDoesNotInsert() {
        service.block(1, false); verify(blocks, never()).save(any());
        service.block(1, true); verify(blocks).save(any()); verify(blocks, times(2)).findByBlockerIdAndBlockedId(2, 1);
    }
    @Test void existingBlockIsSoftUpdatedWithoutReplacingRow() {
        var block = new UserBlock(actor, recipient, NOW);
        when(blocks.findByBlockerIdAndBlockedId(2, 1)).thenReturn(Optional.of(block));
        service.block(1, false); assertFalse(block.isActive()); verify(blocks).save(block);
    }
}
