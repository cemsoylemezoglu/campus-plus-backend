package com.campusplus.backend.service.messaging;

import com.campusplus.backend.dto.messaging.MessagingResponses.*;
import com.campusplus.backend.exception.InvalidRequestException;
import com.campusplus.backend.security.CurrentActor;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scalar queries: no managed message bodies or lazy user graphs in inbox responses. */
@Service @Transactional(readOnly = true)
public class DirectMessageQueryService {
    private final JdbcTemplate jdbc;
    private final DirectMessageWorkspace workspace;
    private final CurrentActor current;
    public DirectMessageQueryService(JdbcTemplate jdbc, DirectMessageWorkspace workspace, CurrentActor current) {
        this.jdbc = jdbc; this.workspace = workspace; this.current = current;
    }
    public CursorPage<Message> messages(long conversationId, String cursor, int limit) {
        limit(limit); long actor = current.userId(); workspace.readable(conversationId);
        var position = MessageCursor.decode(cursor, actor, conversationId);
        String sql = "select m.id,m.conversation_id,m.sender_id,m.message_number,m.body,m.created_at from public.direct_messages m "
                + "join public.direct_conversations c on c.id=m.conversation_id "
                + "where m.conversation_id=? and (?=c.lower_user_id or ?=c.higher_user_id) "
                + (position == null ? "" : "and m.message_number<? ") + "order by m.message_number desc limit ?";
        var args = new ArrayList<Object>(List.of(conversationId, actor, actor));
        if (position != null) args.add(position.beforeNumber()); args.add(limit + 1);
        var rows = jdbc.query(sql, (rs, n) -> message(rs), args.toArray());
        boolean more = rows.size() > limit; var content = rows.subList(0, Math.min(limit, rows.size()));
        String next = more ? new MessageCursor(actor, conversationId, content.getLast().messageNumber()).encode() : null;
        return new CursorPage<>(content, next);
    }
    public CursorPage<Conversation> conversations(String cursor, int limit) {
        limit(limit); long actor = current.userId(); var position = ConversationCursor.decode(cursor, actor);
        String sql = "select c.id,c.lower_user_id,c.higher_user_id,c.last_message_at,"
                + "m.id as message_id,m.sender_id,m.message_number,m.body,m.created_at,"
                + "(select count(*) from public.direct_messages u where u.conversation_id=c.id and u.sender_id<>s.user_id "
                + "and u.message_number>s.last_read_message_number) as unread_count "
                + "from public.direct_conversations c join public.direct_conversation_states s on s.conversation_id=c.id and s.user_id=? "
                + "join public.direct_messages m on m.conversation_id=c.id and m.message_number=c.last_message_number "
                + "where (?=c.lower_user_id or ?=c.higher_user_id) "
                + (position == null ? "" : "and (c.last_message_at,c.id)<(?,?) ")
                + "order by c.last_message_at desc,c.id desc limit ?";
        var args = new ArrayList<Object>(List.of(actor, actor, actor));
        if (position != null) { args.add(Timestamp.from(position.time())); args.add(position.conversationId()); }
        args.add(limit + 1);
        var rows = jdbc.query(sql, (rs, n) -> {
            long id = rs.getLong("id"), lower = rs.getLong("lower_user_id"), higher = rs.getLong("higher_user_id");
            var last = new Message(rs.getLong("message_id"), id, rs.getLong("sender_id"), rs.getLong("message_number"),
                    rs.getString("body"), rs.getTimestamp("created_at").toInstant());
            return new Conversation(id, actor == lower ? higher : lower, last, rs.getLong("unread_count"),
                    rs.getTimestamp("last_message_at").toInstant());
        }, args.toArray());
        boolean more = rows.size() > limit; var content = rows.subList(0, Math.min(limit, rows.size()));
        String next = more ? new ConversationCursor(actor, content.getLast().lastMessageAt(), content.getLast().id()).encode() : null;
        return new CursorPage<>(content, next);
    }
    public UnreadCount unread() {
        long actor = current.userId();
        Long count = jdbc.queryForObject("select count(*) from public.direct_messages m "
                + "join public.direct_conversation_states s on s.conversation_id=m.conversation_id and s.user_id=? "
                + "join public.direct_conversations c on c.id=m.conversation_id "
                + "where m.sender_id<>? and m.message_number>s.last_read_message_number "
                + "and (?=c.lower_user_id or ?=c.higher_user_id)", Long.class, actor, actor, actor, actor);
        return new UnreadCount(count == null ? 0 : count);
    }
    public BlockPage blocks(Long beforeUserId, int limit) {
        limit(limit); if (beforeUserId != null && beforeUserId <= 0) throw new InvalidRequestException("Geçersiz engel cursor değeri.");
        var args = new ArrayList<Object>(); args.add(current.userId());
        if (beforeUserId != null) args.add(beforeUserId); args.add(limit + 1);
        var rows = jdbc.query("select blocked_id,updated_at from public.user_blocks where blocker_id=? and active "
                + (beforeUserId == null ? "" : "and blocked_id<? ") + "order by blocked_id desc limit ?",
                (rs, n) -> new Block(rs.getLong("blocked_id"), rs.getTimestamp("updated_at").toInstant()), args.toArray());
        boolean more = rows.size() > limit; var content = rows.subList(0, Math.min(limit, rows.size()));
        return new BlockPage(content, more ? content.getLast().userId() : null);
    }
    private Message message(ResultSet rs) throws SQLException {
        return new Message(rs.getLong("id"), rs.getLong("conversation_id"), rs.getLong("sender_id"), rs.getLong("message_number"),
                rs.getString("body"), rs.getTimestamp("created_at").toInstant());
    }
    private void limit(int limit) { if (limit < 1 || limit > 100) throw new InvalidRequestException("Sayfa boyutu 1–100 olmalıdır."); }
}
