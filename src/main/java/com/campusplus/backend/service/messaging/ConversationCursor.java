package com.campusplus.backend.service.messaging;

import com.campusplus.backend.exception.InvalidRequestException;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.Base64;

public record ConversationCursor(long userId, Instant time, long conversationId) {
    private static final Instant MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("9999-12-31T23:59:59.999999Z");
    public ConversationCursor {
        if (userId <= 0 || conversationId <= 0 || time == null || time.isBefore(MIN) || time.isAfter(MAX)
                || time.getNano() % 1000 != 0) throw invalid();
    }
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(29)
                .put((byte)1).putLong(userId).putLong(time.getEpochSecond()).putInt(time.getNano()).putLong(conversationId).array());
    }
    public static ConversationCursor decode(String value, long actor) {
        if (actor <= 0) throw invalid();
        if (value == null) return null;
        try {
            if (value.length() != 39 || !value.matches("[A-Za-z0-9_-]+")) throw invalid();
            var buffer = ByteBuffer.wrap(Base64.getUrlDecoder().decode(value));
            if (buffer.remaining() != 29 || buffer.get() != 1) throw invalid();
            long user = buffer.getLong(), seconds = buffer.getLong(); int nanos = buffer.getInt();
            if (nanos < 0 || nanos > 999999999) throw invalid();
            var cursor = new ConversationCursor(user, Instant.ofEpochSecond(seconds, nanos), buffer.getLong());
            if (cursor.userId != actor || !cursor.encode().equals(value)) throw invalid();
            return cursor;
        } catch (IllegalArgumentException | DateTimeException ex) { throw invalid(); }
    }
    private static InvalidRequestException invalid() { return new InvalidRequestException("Konuşma cursor değeri geçersiz."); }
}
