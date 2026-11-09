package com.campusplus.backend.messaging;

import com.campusplus.backend.dto.messaging.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DirectMessageInputTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void blankContentIsRejected(String body) {
        assertFalse(validator.validate(new SendDirectMessageRequest(UUID.randomUUID(), body)).isEmpty());
    }
    @Test void bodyBoundaryAndRequiredIdempotencyKey() {
        assertTrue(validator.validate(new SendDirectMessageRequest(UUID.randomUUID(), "x".repeat(4000))).isEmpty());
        assertFalse(validator.validate(new SendDirectMessageRequest(UUID.randomUUID(), "x".repeat(4001))).isEmpty());
        assertFalse(validator.validate(new SendDirectMessageRequest(null, "text")).isEmpty());
        assertEquals("line1\nline2", new SendDirectMessageRequest(UUID.randomUUID(), " line1\nline2 ").body());
    }
    @Test void conversationCreationValidatesRecipientAndNestedMessage() {
        var message = new SendDirectMessageRequest(UUID.randomUUID(), "Hello");
        assertTrue(validator.validate(new StartDirectConversationRequest(2L, message)).isEmpty());
        assertFalse(validator.validate(new StartDirectConversationRequest(null, message)).isEmpty());
        assertFalse(validator.validate(new StartDirectConversationRequest(0L, message)).isEmpty());
        assertFalse(validator.validate(new StartDirectConversationRequest(-1L, message)).isEmpty());
        assertFalse(validator.validate(new StartDirectConversationRequest(2L, null)).isEmpty());
        assertFalse(validator.validate(new StartDirectConversationRequest(2L, new SendDirectMessageRequest(null, " "))).isEmpty());
    }
    @Test void logsDoNotContainPrivateTextRecipientOrRetryKey() {
        var id = UUID.randomUUID(); var message = new SendDirectMessageRequest(id, "PRIVATE_SENTINEL");
        for (var rendered : new String[]{message.toString(), new StartDirectConversationRequest(987654L, message).toString()}) {
            assertFalse(rendered.contains("PRIVATE_SENTINEL")); assertFalse(rendered.contains(id.toString()));
            assertFalse(rendered.contains("987654"));
        }
    }
}
