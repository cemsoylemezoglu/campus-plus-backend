package com.campusplus.backend.messaging;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.dto.messaging.MessagingResponses.*;
import com.campusplus.backend.service.messaging.*;
import com.campusplus.backend.exception.InvalidRequestException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class DirectMessageDomainTest {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00.123456Z");
    User user(long id) { var u = new User("Student", "student@example.test"); ReflectionTestUtils.setField(u, "id", id); return u; }
    DirectConversation conversation() { var c = new DirectConversation(user(2), user(1), NOW); ReflectionTestUtils.setField(c, "id", 10L); return c; }

    @Test void sequenceAndLastActivityRemainMonotonicWhenClockMovesBack() {
        var c = conversation(); var a = new DirectMessage(c, user(1), UUID.randomUUID(), "first", NOW);
        var b = new DirectMessage(c, user(2), UUID.randomUUID(), "second", NOW.minusSeconds(1));
        assertEquals(1, a.getMessageNumber()); assertEquals(2, b.getMessageNumber());
        assertEquals(NOW, c.getLastMessageAt()); assertEquals(new DirectConversationMembers(1, 2), c.members());
    }
    @Test void senderAndBodyAreValidatedBeforeCounterChanges() {
        var c = conversation();
        assertThrows(IllegalArgumentException.class, () -> new DirectMessage(c, user(3), UUID.randomUUID(), "text", NOW));
        for (String body : Arrays.asList(null, " ", "x".repeat(4001), "a\0b"))
            assertThrows(IllegalArgumentException.class, () -> new DirectMessage(c, user(1), UUID.randomUUID(), body, NOW));
        assertThrows(IllegalArgumentException.class, () -> new DirectMessage(c, user(1), null, "text", NOW));
        assertEquals(0, c.getLastMessageNumber());
    }
    @Test void readPositionCannotMoveBackwardsOrBeyondThisConversation() {
        var c = conversation(); var state = new DirectConversationState(c, 1, NOW);
        new DirectMessage(c, user(1), UUID.randomUUID(), "a", NOW);
        new DirectMessage(c, user(2), UUID.randomUUID(), "b", NOW);
        state.markRead(2, NOW); state.markRead(1, NOW); assertEquals(2, state.getLastReadMessageNumber());
        assertThrows(IllegalArgumentException.class, () -> state.markRead(3, NOW));
        assertThrows(IllegalArgumentException.class, () -> state.markRead(0, NOW));
        assertThrows(IllegalArgumentException.class, () -> new DirectConversationState(c, 3, NOW));
    }
    @Test void blocksAreIdempotentSoftStateAndSelfBlockIsRejected() {
        var b = new UserBlock(user(1), user(2), NOW); assertTrue(b.isActive());
        b.setActive(true, NOW); b.setActive(false, NOW); b.setActive(false, NOW); assertFalse(b.isActive());
        assertThrows(IllegalArgumentException.class, () -> new UserBlock(user(1), user(1), NOW));
    }
    @Test void entityAndNestedResponseLogsDoNotRevealContent() {
        var c = conversation(); var m = new DirectMessage(c, user(1), UUID.randomUUID(), "PRIVATE_SENTINEL", NOW);
        var response = Message.from(m); var summary = new Conversation(10, 2, response, 1, NOW);
        for (Object value : List.of(c, m, response, summary, new CursorPage<>(List.of(summary), "opaque")))
            assertFalse(value.toString().contains("PRIVATE_SENTINEL"));
    }
    @Test void messageCursorIsBoundToUserAndConversation() {
        var c = new MessageCursor(1, 10, Long.MAX_VALUE);
        assertEquals(c, MessageCursor.decode(c.encode(), 1, 10));
        assertThrows(InvalidRequestException.class, () -> MessageCursor.decode(c.encode(), 2, 10));
        assertThrows(InvalidRequestException.class, () -> MessageCursor.decode(c.encode(), 1, 20));
        assertNull(MessageCursor.decode(null, 1, 10));
    }
    @Test void inboxCursorPreservesMicrosecondPrecisionAndActorBinding() {
        var c = new ConversationCursor(1, NOW, 10); assertEquals(c, ConversationCursor.decode(c.encode(), 1));
        assertThrows(InvalidRequestException.class, () -> ConversationCursor.decode(c.encode(), 2));
        assertThrows(InvalidRequestException.class, () -> new ConversationCursor(1, NOW.plusNanos(1), 10));
        assertNull(ConversationCursor.decode(null, 1));
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "x", "a/b", "broken", "===", "a"})
    void malformedCursorCannotReachPersistence(String value) {
        assertThrows(InvalidRequestException.class, () -> MessageCursor.decode(value, 1, 10));
        assertThrows(InvalidRequestException.class, () -> ConversationCursor.decode(value, 1));
    }
    @Test void corruptedVersionAndPositionsAreRejected() {
        for (long[] values : new long[][]{{2,1,10,1},{1,0,10,1},{1,1,0,1},{1,1,10,0}}) {
            String value = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(25)
                    .put((byte)values[0]).putLong(values[1]).putLong(values[2]).putLong(values[3]).array());
            assertThrows(InvalidRequestException.class, () -> MessageCursor.decode(value, 1, 10));
        }
        String invalid = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(29)
                .put((byte)1).putLong(1).putLong(0).putInt(-1).putLong(10).array());
        assertThrows(InvalidRequestException.class, () -> ConversationCursor.decode(invalid, 1));
    }
}
