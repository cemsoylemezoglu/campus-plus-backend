package com.campusplus.backend.service.messaging;

import com.campusplus.backend.exception.InvalidRequestException;
import java.nio.ByteBuffer;
import java.util.Base64;

/** Versioned, user/conversation-bound position. Authorization is always checked separately. */
public record MessageCursor(long userId, long conversationId, long beforeNumber) {
    public MessageCursor {
        if (userId <= 0 || conversationId <= 0 || beforeNumber <= 0) throw invalid();
    }
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(25)
                .put((byte)1).putLong(userId).putLong(conversationId).putLong(beforeNumber).array());
    }
    public static MessageCursor decode(String value, long userId, long conversationId) {
        if (userId <= 0 || conversationId <= 0) throw invalid();
        if (value == null) return null;
        try {
            if (value.length() != 34 || !value.matches("[A-Za-z0-9_-]+")) throw invalid();
            var bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(value));
            if (bytes.remaining() != 25 || bytes.get() != 1) throw invalid();
            var cursor = new MessageCursor(bytes.getLong(), bytes.getLong(), bytes.getLong());
            if (cursor.userId != userId || cursor.conversationId != conversationId || !value.equals(cursor.encode())) throw invalid();
            return cursor;
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }
    private static InvalidRequestException invalid() { return new InvalidRequestException("Mesaj cursor değeri geçersiz."); }
}
