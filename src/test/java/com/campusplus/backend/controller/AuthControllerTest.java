package com.campusplus.backend.controller;

import com.campusplus.backend.config.SecurityConfig;
import com.campusplus.backend.dto.RegisterRequest;
import com.campusplus.backend.entity.User;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.UserRepository;
import com.campusplus.backend.service.AuthService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.support.TestPropertySourceUtils;
import com.campusplus.backend.security.JwtTestKeys;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

// Real MVC, application security filter chain, AuthService and BCrypt. Only repository is mocked.
// No application.properties, DataSource, Hibernate or live database is loaded.
class AuthControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private UserRepository repository;
    private PasswordEncoder encoder;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AuthController.class, AuthService.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "JWT_SECRET=" + JwtTestKeys.newSecret());
        context.register(TestConfig.class);
        context.refresh();
        repository = context.getBean(UserRepository.class);
        encoder = context.getBean(PasswordEncoder.class);
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        mvc = webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    private String json(String password) {
        return "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":"
                + (password == null ? "null" : "\"" + password + "\"") + "}";
    }

    @Test
    void anonymousRegistrationHashesPasswordAndReturnsOnlyPublicFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ValidPass123!")))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":2,"name":"Ada","email":"ada@example.com"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        String hash = (String) ReflectionTestUtils.getField(captor.getValue(), "passwordHash");
        assertNotNull(hash);
        assertNotEquals("ValidPass123!", hash);
        assertTrue(hash.startsWith("$2a$12$"));
        assertTrue(encoder.matches("ValidPass123!", hash));
        assertFalse(encoder.matches("WrongPass123!", hash));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "        "})
    void rejectsMissingBlankOrShortPassword(String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.password").exists());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "unicode"})
    void rejectsPasswordOver72Utf8Bytes(String kind) throws Exception {
        String password = kind.equals("ascii") ? "a".repeat(73) : "é".repeat(37);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.password").exists());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "unicode"})
    void acceptsExactly72Utf8Bytes(String kind) throws Exception {
        String password = kind.equals("ascii") ? "a".repeat(72) : "é".repeat(36);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(password)))
                .andExpect(status().isCreated());
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        assertTrue(encoder.matches(password,
                (String) ReflectionTestUtils.getField(captor.getValue(), "passwordHash")));
    }

    @Test
    void rejectsInvalidNameAndEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"email\":\"invalid\",\"password\":\"ValidPass123!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFieldsExceedingDatabaseLengths() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(101) + "\",\"email\":\""
                                + "a".repeat(244) + "@example.com\",\"password\":\"ValidPass123!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDuplicateEmailWithoutSaving() throws Exception {
        when(repository.existsByEmail("ada@example.com")).thenReturn(true);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ValidPass123!")))
                .andExpect(status().isConflict());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentEmailConflictIsHandledWithoutLeakingDatabaseDetails() throws Exception {
        when(repository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("private SQL and bind values"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ValidPass123!")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                        "İşlem mevcut kayıtlarla çakışıyor. Gönderdiğiniz bilgileri kontrol ediniz."));
    }

    @Test
    void otherEndpointsRemainProtectedWithoutBearerAuthentication() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void requestToStringRedactsPassword() {
        var request = new RegisterRequest("Ada", "ada@example.com", "ValidPass123!");
        assertFalse(request.toString().contains(request.password()));
        assertTrue(request.toString().contains("[REDACTED]"));
    }

    @Test
    void bcryptSaltsIdenticalPasswordsDifferently() {
        String first = encoder.encode("ValidPass123!");
        String second = encoder.encode("ValidPass123!");
        assertNotEquals(first, second);
        assertTrue(encoder.matches("ValidPass123!", first));
        assertTrue(encoder.matches("ValidPass123!", second));
    }
}
