package com.campusplus.backend.service.clubinteraction;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.exception.InvalidRequestException;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.Base64;

/** Opaque keyset position, bound to both source namespace and thread. Not an authorization token. */
public record ClubCommentCursor(Instant time, long id, ClubSourceRef source, long rootId) {
    private static final Instant MIN_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("9999-12-31T23:59:59.999999Z");
    private static final int BYTES = 38;
    private static final int ENCODED_LENGTH = 51;

    public ClubCommentCursor {
        if (time == null || time.isBefore(MIN_TIME) || time.isAfter(MAX_TIME)
                || id <= 0 || source == null || rootId < 0) throw invalid();
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(BYTES)
                .put((byte) 1).put(source.kind().cursorTag()).putLong(time.getEpochSecond())
                .putInt(time.getNano()).putLong(id).putLong(source.id()).putLong(rootId).array());
    }

    public static ClubCommentCursor decode(String value, ClubSourceRef expectedSource, long expectedRootId) {
        if (expectedSource == null || expectedRootId < 0) throw invalid();
        if (value == null) return null;
        try {
            if (value.length() != ENCODED_LENGTH || !value.matches("[A-Za-z0-9_-]+")) throw invalid();
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length != BYTES) throw invalid();
            var buffer = ByteBuffer.wrap(bytes);
            if (buffer.get() != 1) throw invalid();
            var kind = ClubSourceKind.fromCursorTag(buffer.get());
            long seconds = buffer.getLong();
            int nanos = buffer.getInt();
            if (nanos < 0 || nanos > 999_999_999) throw invalid();
            var cursor = new ClubCommentCursor(Instant.ofEpochSecond(seconds, nanos), buffer.getLong(),
                    new ClubSourceRef(kind, buffer.getLong()), buffer.getLong());
            if (!cursor.source.equals(expectedSource) || cursor.rootId != expectedRootId
                    || !cursor.encode().equals(value)) throw invalid();
            return cursor;
        } catch (IllegalArgumentException | DateTimeException e) {
            throw invalid();
        }
    }

    private static InvalidRequestException invalid() {
        return new InvalidRequestException("Yorum cursor değeri geçersiz.");
    }
}
