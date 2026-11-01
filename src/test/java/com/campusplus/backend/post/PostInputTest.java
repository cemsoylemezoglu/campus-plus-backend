package com.campusplus.backend.post;

import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.entity.PostAudience;
import com.campusplus.backend.exception.InvalidRequestException;
import com.campusplus.backend.service.post.FeedCursor;
import jakarta.validation.*;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class PostInputTest {
    @ParameterizedTest @ValueSource(longs = {1, 42, Long.MAX_VALUE})
    void cursorRetainsNanosecondsAndBothSortKeys(long id) {
        var cursor = new FeedCursor(Instant.parse("2030-03-04T12:13:14.123456Z"), id, 10L);
        assertEquals(cursor, FeedCursor.decode(cursor.encode(), 10L));
    }
    @Test void cursorCannotChangeFilterOrConfuseNullWithPositiveClub() {
        var cursor = new FeedCursor(Instant.EPOCH, 1, 10L);
        assertThrows(InvalidRequestException.class, () -> FeedCursor.decode(cursor.encode(), 20L));
        assertThrows(InvalidRequestException.class, () -> FeedCursor.decode(cursor.encode(), null));
        var all = new FeedCursor(Instant.EPOCH, 1, null);
        assertEquals(all, FeedCursor.decode(all.encode(), null));
        assertNull(FeedCursor.decode(null, 10L));
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "=", "a/b", "a+b", "a"})
    void malformedCursorIsBadRequest(String text) { assertThrows(InvalidRequestException.class, () -> FeedCursor.decode(text, null)); }
    String encoded(int version, long seconds, int nanos, long id, long club) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(29)
                .put((byte) version).putLong(seconds).putInt(nanos).putLong(id).putLong(club).array());
    }
    @ParameterizedTest @CsvSource({"2,0,0,1,0", "1,0,-1,1,0", "1,0,1000000000,1,0", "1,0,0,0,0", "1,0,0,1,-1", "1,9223372036854775807,0,1,0", "1,253402300800,0,1,0"})
    void malformedFieldsAreRejectedBeforeTheyReachJdbc(int version, long seconds, int nanos, long id, long club) {
        assertThrows(InvalidRequestException.class, () -> FeedCursor.decode(encoded(version, seconds, nanos, id, club), null));
    }
    @Test void dtoToStringDoesNotRevealPrivateContent() {
        String privateText = "private-sentinel-text";
        for (Object dto : new Object[]{new CreateStudentPostRequest(privateText), new CreateClubPostRequest(privateText, null),
                new UpdatePostRequest(0L, privateText, null), new PostReasonRequest(0L, privateText)}) assertFalse(dto.toString().contains(privateText));
    }
    @Test void boundaryInputsAndWhitespaceAreValidatedAfterNormalization() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals("text", new CreateStudentPostRequest(" text ").body());
            assertEquals(PostAudience.UNIVERSITY_STUDENTS, new CreateClubPostRequest("text", null).audience());
            assertTrue(validator.validate(new CreateStudentPostRequest("x".repeat(10000))).isEmpty());
            assertFalse(validator.validate(new CreateStudentPostRequest("x".repeat(10001))).isEmpty());
            assertFalse(validator.validate(new UpdatePostRequest(null, "text", null)).isEmpty());
            assertFalse(validator.validate(new PostReasonRequest(0L, "\t\n")).isEmpty());
        }
    }
}
