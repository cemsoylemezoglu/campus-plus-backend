package com.campusplus.backend.service.post;

import com.campusplus.backend.exception.InvalidRequestException;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;

/** Versioned, fixed-length cursor; never an authentication or authorization credential. */
public record FeedCursor(Instant time, long id, Long clubId) {
    public String encode() {
        ByteBuffer data = ByteBuffer.allocate(29);
        data.put((byte) 1).putLong(time.getEpochSecond()).putInt(time.getNano()).putLong(id).putLong(clubId == null ? 0 : clubId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.array());
    }
    public static FeedCursor decode(String value, Long filter) {
        if (value == null) return null;
        try {
            if (value.length() != 39 || !value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length != 29) throw new IllegalArgumentException();
            ByteBuffer data = ByteBuffer.wrap(bytes);
            if (data.get() != 1) throw new IllegalArgumentException();
            long seconds = data.getLong(); int nanos = data.getInt(); long id = data.getLong(); long club = data.getLong();
            if (nanos < 0 || nanos > 999999999 || id <= 0 || club < 0) throw new IllegalArgumentException();
            FeedCursor cursor = new FeedCursor(Instant.ofEpochSecond(seconds, nanos), id, club == 0 ? null : club);
            if (cursor.time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || cursor.time.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) throw new IllegalArgumentException();
            // Canonical representation, bound to the selected filter. No signed secret needed.
            if (!Objects.equals(filter, cursor.clubId) || !cursor.encode().equals(value)) throw new IllegalArgumentException();
            return cursor;
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw new InvalidRequestException("Akış cursor değeri geçersiz; ilk sayfadan başlayınız.");
        }
    }
}
