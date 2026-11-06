package com.campusplus.backend.comment;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.exception.InvalidRequestException;
import com.campusplus.backend.service.clubinteraction.ClubCommentCursor;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ClubCommentCursorTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00.123456Z");
    private static final ClubSourceRef EVENT = new ClubSourceRef(ClubSourceKind.EVENT, 7);

    private String raw(byte version, byte kind, long seconds, int nanos, long id, long source, long root) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(38)
                .put(version).put(kind).putLong(seconds).putInt(nanos).putLong(id).putLong(source).putLong(root).array());
    }

    @ParameterizedTest @EnumSource(ClubSourceKind.class)
    void preservesSourceThreadAndFullTimestampPrecision(ClubSourceKind kind) {
        var source = new ClubSourceRef(kind, Long.MAX_VALUE);
        for (long root : new long[]{0, 19, Long.MAX_VALUE}) {
            var cursor = new ClubCommentCursor(NOW, Long.MAX_VALUE, source, root);
            assertEquals(51, cursor.encode().length());
            assertEquals(cursor, ClubCommentCursor.decode(cursor.encode(), source, root));
        }
    }

    @Test void matchingNumericIdsDoNotPermitCrossSourceOrCrossThreadReuse() {
        String value = new ClubCommentCursor(NOW, 99, EVENT, 0).encode();
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(value,
                new ClubSourceRef(ClubSourceKind.ANNOUNCEMENT, 7), 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(value,
                new ClubSourceRef(ClubSourceKind.EVENT, 8), 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(value, EVENT, 5));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(
                new ClubCommentCursor(NOW, 99, EVENT, 5).encode(), EVENT, 0));
    }

    @Test void missingCursorIsFirstPageButInvalidExpectedContextIsRejected() {
        assertNull(ClubCommentCursor.decode(null, EVENT, 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(null, null, 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(null, EVENT, -1));
        assertThrows(InvalidRequestException.class, () -> new ClubSourceRef(null, 7));
        assertThrows(InvalidRequestException.class, () -> new ClubSourceRef(ClubSourceKind.EVENT, 0));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "!", "a", "abc=", "../../secret"})
    void rejectsMalformedInputWithoutIncludingItInErrors(String input) {
        var error = assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(input, EVENT, 0));
        assertEquals("Yorum cursor değeri geçersiz.", error.getMessage());
    }

    @Test void rejectsUnsupportedVersionKindAndNonCanonicalBase64() {
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(raw((byte) 2, (byte) 1, NOW.getEpochSecond(), 0, 1, 7, 0), EVENT, 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(raw((byte) 1, (byte) 3, NOW.getEpochSecond(), 0, 1, 7, 0), EVENT, 0));
        String canonical = new ClubCommentCursor(NOW, 1, EVENT, 0).encode();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int last = alphabet.indexOf(canonical.charAt(50));
        String alternative = canonical.substring(0, 50) + alphabet.charAt(last | 1);
        assertArrayEquals(Base64.getUrlDecoder().decode(canonical), Base64.getUrlDecoder().decode(alternative));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(alternative, EVENT, 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(canonical + "=", EVENT, 0));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 1_000_000_000, Integer.MAX_VALUE})
    void rejectsOutOfRangeNanosecondsInsteadOfNormalizingThem(int nanos) {
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(
                raw((byte) 1, (byte) 1, NOW.getEpochSecond(), nanos, 1, 7, 0), EVENT, 0));
    }

    @Test void rejectsInvalidIdsRootsAndTimestampBounds() {
        for (long id : new long[]{0, -1}) assertThrows(InvalidRequestException.class, () ->
                ClubCommentCursor.decode(raw((byte) 1, (byte) 1, NOW.getEpochSecond(), 0, id, 7, 0), EVENT, 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(raw((byte) 1, (byte) 1, NOW.getEpochSecond(), 0, 1, 0, 0), EVENT, 0));
        assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(raw((byte) 1, (byte) 1, NOW.getEpochSecond(), 0, 1, 7, -1), EVENT, 0));
        for (long seconds : new long[]{Long.MIN_VALUE, Long.MAX_VALUE,
                Instant.parse("0001-01-01T00:00:00Z").getEpochSecond() - 1,
                Instant.parse("+10000-01-01T00:00:00Z").getEpochSecond()}) {
            assertThrows(InvalidRequestException.class, () -> ClubCommentCursor.decode(
                    raw((byte) 1, (byte) 1, seconds, 0, 1, 7, 0), EVENT, 0));
        }
    }

    @Test void acceptsDatabaseDateBoundsAndDeterministicRoundTrips() {
        for (Instant time : List.of(Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999Z"))) {
            var cursor = new ClubCommentCursor(time, 1, EVENT, 0);
            assertEquals(cursor, ClubCommentCursor.decode(cursor.encode(), EVENT, 0));
        }
        var random = new Random(10);
        for (int i = 0; i < 1000; i++) {
            var source = new ClubSourceRef(i % 2 == 0 ? ClubSourceKind.EVENT : ClubSourceKind.ANNOUNCEMENT, random.nextLong(1, Long.MAX_VALUE));
            var cursor = new ClubCommentCursor(NOW.plusSeconds(random.nextInt(100_000)), random.nextLong(1, Long.MAX_VALUE), source, i);
            assertEquals(cursor, ClubCommentCursor.decode(cursor.encode(), source, i));
        }
    }
}
