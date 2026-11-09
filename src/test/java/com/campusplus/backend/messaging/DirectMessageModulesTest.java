package com.campusplus.backend.messaging;

import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.DirectMessageController;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.messaging.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Actual JWT + controller/service/access policy. Persistence is mocked; real PG tests are separate. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DirectMessageModulesTest {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, CurrentActor.class, DirectMessagePolicy.class, DirectMessageWorkspace.class,
            DirectMessageService.class, DirectMessageQueryService.class, DirectMessageController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean StudentProfileRepository profiles() { return mock(StudentProfileRepository.class); }
        @Bean UniversityRepository universities() { return mock(UniversityRepository.class); }
        @Bean DirectConversationRepository conversations() { return mock(DirectConversationRepository.class); }
        @Bean DirectMessageRepository messages() { return mock(DirectMessageRepository.class); }
        @Bean DirectConversationStateRepository states() { return mock(DirectConversationStateRepository.class); }
        @Bean UserBlockRepository blocks() { return mock(UserBlockRepository.class); }
        @Bean EntityManager em() { return mock(EntityManager.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean @Primary Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    AnnotationConfigWebApplicationContext context; MockMvc mvc; JwtEncoder encoder;
    final JsonMapper json = JsonMapper.builder().build();
    UserRepository users; DirectConversationRepository conversations; DirectMessageRepository messages;
    UserBlockRepository blocks; StudentProfileRepository profiles; University university;
    final Map<Long, User> people = new HashMap<>();
    @BeforeAll void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "JWT_SECRET=" + JwtTestKeys.newSecret());
        context.register(Config.class); context.refresh(); encoder = context.getBean(JwtEncoder.class);
        users = context.getBean(UserRepository.class); conversations = context.getBean(DirectConversationRepository.class);
        messages = context.getBean(DirectMessageRepository.class); blocks = context.getBean(UserBlockRepository.class); profiles = context.getBean(StudentProfileRepository.class);
        mvc = webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterAll void close() { if (context != null) context.close(); }
    @BeforeEach void setup() {
        reset(users, conversations, messages, blocks, profiles, context.getBean(UniversityRepository.class)); people.clear();
        university = new University("UNI", "University", "City", "TR", null, NOW); ReflectionTestUtils.setField(university, "id", 10L);
        for (long id = 1; id <= 3; id++) {
            var user = new User("Student", "student" + id + "@example.test"); user.setPasswordHash("unused-test-hash");
            ReflectionTestUtils.setField(user, "id", id); people.put(id, user);
            when(users.findById(id)).thenReturn(Optional.of(user)); when(users.findByIdForUpdate(id)).thenReturn(Optional.of(user));
            when(profiles.findById(id)).thenReturn(Optional.of(new StudentProfile(user, university, "Engineering", null, null, NOW)));
        }
        people.get(3L).grantRole(Role.ADMIN); people.get(3L).grantRole(Role.CLUB_ADMIN);
        when(context.getBean(UniversityRepository.class).findByIdForShare(10L)).thenReturn(Optional.of(university));
        var c = new DirectConversation(people.get(1L), people.get(2L), NOW); ReflectionTestUtils.setField(c, "id", 10L);
        when(conversations.findById(10L)).thenReturn(Optional.of(c));
        when(messages.saveAndFlush(any())).thenAnswer(i -> { DirectMessage m = i.getArgument(0); ReflectionTestUtils.setField(m, "id", 100L); return m; });
    }
    ResultActions request(String method, String path, Long user, Object body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path).contentType("application/json");
        if (user != null) {
            var claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                    .subject(user.toString()).issuedAt(NOW).expiresAt(NOW.plusSeconds(120)).build();
            request.header("Authorization", "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue());
        }
        if (body != null) request.content(json.writeValueAsString(body)); return mvc.perform(request);
    }
    Map<String, Object> message(String text) { return Map.of("clientMessageId", UUID.randomUUID().toString(), "body", text); }
    @Test void allRoutesRequireBearerAuthentication() throws Exception {
        for (String path : List.of("/api/me/conversations", "/api/me/conversations/10/messages", "/api/me/conversations/unread-count", "/api/me/blocks"))
            request("GET", path, null, null).andExpect(status().isUnauthorized());
        request("POST", "/api/me/conversations/10/messages", null, message("x")).andExpect(status().isUnauthorized());
        request("PUT", "/api/me/blocks/2", null, null).andExpect(status().isUnauthorized());
    }
    @Test void adminAndClubAdminCannotInspectOrWriteOthersConversation() throws Exception {
        request("GET", "/api/me/conversations/10/messages", 3L, null).andExpect(status().isNotFound());
        request("POST", "/api/me/conversations/10/messages", 3L, message("intrusion")).andExpect(status().isNotFound());
        request("PUT", "/api/me/conversations/10/read", 3L, Map.of("throughMessageNumber", 1)).andExpect(status().isNotFound());
        verify(messages, never()).saveAndFlush(any());
    }
    @Test void authenticatedSenderCannotOverridePrincipalWithBodyField() throws Exception {
        var body = new HashMap<>(message("hello")); body.put("senderId", 3);
        request("POST", "/api/me/conversations/10/messages", 1L, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.senderId").value(1)).andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void recipientBlockAndMissingProfileHaveSameError() throws Exception {
        when(blocks.existsByBlockerIdAndBlockedIdAndActiveTrue(2, 1)).thenReturn(true);
        String block = request("POST", "/api/me/conversations/10/messages", 1L, message("x")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        when(blocks.existsByBlockerIdAndBlockedIdAndActiveTrue(2, 1)).thenReturn(false); when(profiles.findById(2L)).thenReturn(Optional.empty());
        String missing = request("POST", "/api/me/conversations/10/messages", 1L, message("x")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(block).path("message"), json.readTree(missing).path("message"));
    }
    @Test void lostStudentRoleClosesSendButNotOwnHistory() throws Exception {
        people.get(1L).grantRole(Role.ADMIN); people.get(1L).revokeRole(Role.STUDENT);
        request("POST", "/api/me/conversations/10/messages", 1L, message("x")).andExpect(status().isNotFound());
        request("GET", "/api/me/conversations/10/messages", 1L, null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void validationRejectsBadKeyBlankNulAndOversizedText() throws Exception {
        for (Object body : List.of(Map.of("body", "hello"), Map.of("clientMessageId", "bad", "body", "x"), message(" "), message("a\0b"), message("x".repeat(4001))))
            request("POST", "/api/me/conversations/10/messages", 1L, body).andExpect(status().isBadRequest());
        request("POST", "/api/me/conversations", 1L, Map.of("recipientUserId", 0, "message", message("x"))).andExpect(status().isBadRequest());
        request("PUT", "/api/me/conversations/10/read", 1L, Map.of("throughMessageNumber", 0)).andExpect(status().isBadRequest());
    }
    @Test void cursorsAndBoundsCannotBeUsedAcrossAccountsOrConversations() throws Exception {
        var cursor = new MessageCursor(2, 10, 1).encode();
        request("GET", "/api/me/conversations/10/messages?cursor=" + cursor, 1L, null).andExpect(status().isBadRequest());
        request("GET", "/api/me/conversations?limit=101", 1L, null).andExpect(status().isBadRequest());
        request("GET", "/api/me/blocks?beforeUserId=0", 1L, null).andExpect(status().isBadRequest());
    }
    @Test void privateListsAndCountsAreNotCached() throws Exception {
        for (String path : List.of("/api/me/conversations", "/api/me/conversations/unread-count", "/api/me/blocks"))
            request("GET", path, 1L, null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void blockAndUnblockAreSoftIdempotentAndSelfBlockIsRejected() throws Exception {
        request("PUT", "/api/me/blocks/2", 1L, null).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        request("DELETE", "/api/me/blocks/2", 1L, null).andExpect(status().isNoContent());
        request("PUT", "/api/me/blocks/1", 1L, null).andExpect(status().isNotFound());
        verify(blocks, never()).delete(any());
    }
}
