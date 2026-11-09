package com.campusplus.backend.messaging;

import com.campusplus.backend.dto.messaging.MessagingResponses.*;
import com.campusplus.backend.exception.InvalidRequestException;
import com.campusplus.backend.security.CurrentActor;
import com.campusplus.backend.service.messaging.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectMessageQueryTest {
    JdbcTemplate jdbc; DirectMessageWorkspace workspace; CurrentActor current; DirectMessageQueryService queries;
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class); workspace = mock(DirectMessageWorkspace.class); current = mock(CurrentActor.class);
        when(current.userId()).thenReturn(1L); queries = new DirectMessageQueryService(jdbc,workspace,current);
    }
    @Test @SuppressWarnings({"unchecked","rawtypes"})
    void messagePaginationUsesExclusiveNumberAndDoesNotReturnLookaheadRow() {
        var first = new Message(3L,10L,2,3,"a",NOW); var second = new Message(2L,10L,2,2,"b",NOW); var third = new Message(1L,10L,2,1,"c",NOW);
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn((List)List.of(first,second,third));
        var page = queries.messages(10,new MessageCursor(1,10,4).encode(),2);
        assertEquals(List.of(first,second),page.content()); assertEquals(2,MessageCursor.decode(page.nextCursor(),1,10).beforeNumber());
        var calls = mockingDetails(jdbc).getInvocations(); var call = calls.iterator().next();
        String sql = (String)call.getRawArguments()[0]; Object[] args = (Object[])call.getRawArguments()[2];
        assertTrue(sql.contains("m.message_number<?")); assertTrue(sql.contains("c.lower_user_id"));
        assertEquals(sql.chars().filter(c -> c=='?').count(),args.length); assertEquals(3,args[args.length-1]);
        verify(workspace).readable(10);
    }
    @Test @SuppressWarnings({"unchecked","rawtypes"})
    void inboxCursorUsesLastReturnedConversationAndUserScopedQuery() {
        var message = new Message(1L,10L,2,1,"text",NOW);
        var a = new Conversation(20,2,message,1,NOW); var b = new Conversation(10,3,message,1,NOW);
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn((List)List.of(a,b));
        var page = queries.conversations(null,1); assertEquals(List.of(a),page.content());
        assertEquals(new ConversationCursor(1,NOW,20),ConversationCursor.decode(page.nextCursor(),1));
        var call = mockingDetails(jdbc).getInvocations().iterator().next();
        assertTrue(((String)call.getRawArguments()[0]).contains("s.user_id=?"));
    }
    @Test void invalidLimitsAndForeignCursorNeverReachSql() {
        assertThrows(InvalidRequestException.class, () -> queries.conversations(null,0));
        assertThrows(InvalidRequestException.class, () -> queries.messages(10,null,101));
        assertThrows(InvalidRequestException.class, () -> queries.blocks(0L,20));
        assertThrows(InvalidRequestException.class, () -> queries.messages(10,new MessageCursor(2,10,1).encode(),20));
        verifyNoInteractions(jdbc);
    }
    @Test void emptyPagesHaveNoCursor() {
        assertNull(queries.messages(10,null,20).nextCursor());
        assertNull(queries.conversations(null,20).nextCursor());
        assertNull(queries.blocks(null,20).nextBeforeUserId());
    }
    @Test void countsAreCurrentUserScopedAndDoNotIncludeOwnMessages() {
        assertEquals(0,queries.unread().count());
        var call = mockingDetails(jdbc).getInvocations().iterator().next(); String sql = (String)call.getRawArguments()[0];
        assertTrue(sql.contains("m.sender_id<>?")); assertTrue(sql.contains("m.message_number>s.last_read_message_number"));
        Object[] args = (Object[])call.getRawArguments()[2]; for (Object id : args) assertEquals(1L,id);
    }
}
