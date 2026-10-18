package com.campusplus.backend.security;

import com.campusplus.backend.config.JwtConfig;
import com.campusplus.backend.config.SecurityConfig;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.LoginRequest;
import com.campusplus.backend.dto.LoginResponse;
import com.campusplus.backend.entity.User;
import com.campusplus.backend.entity.Role;
import org.junit.jupiter.params.provider.EnumSource;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.UserRepository;
import com.campusplus.backend.service.*;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

// Production SecurityFilterChain, real JWT encoder/decoder, MVC, services and BCrypt; no database.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtAuthenticationTest {
    private AnnotationConfigWebApplicationContext context;
    private UserRepository repository;
    private PasswordEncoder passwords;
    private JwtEncoder encoder;
    private JwtDecoder decoder;
    private MockMvc mvc;
    private User user;
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({SecurityConfig.class, LoginController.class, AuthController.class, UserController.class,
            LoginService.class, AuthService.class, UserService.class, JwtTokenService.class,
            GlobalExceptionHandler.class, ProtectedController.class})
    static class TestConfig {
        @Bean UserRepository repository() { return mock(UserRepository.class); }
        @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String admin() { return "admin"; }

        @GetMapping("/test/identity")
        public Map<String, String> identity(Authentication authentication) {
            return Map.of("subject", authentication.getName());
        }
    }

    @BeforeAll
    void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "JWT_SECRET=" + JwtTestKeys.newSecret());
        context.register(TestConfig.class);
        context.refresh();
        repository = context.getBean(UserRepository.class);
        passwords = context.getBean(PasswordEncoder.class);
        encoder = context.getBean(JwtEncoder.class);
        decoder = context.getBean(JwtDecoder.class);
        user = new User("Ada", "ada@example.com");
        ReflectionTestUtils.setField(user, "id", 2L);
        user.setPasswordHash(passwords.encode("ValidPass123!"));
        mvc = webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @BeforeEach
    void resetRepository() {
        reset(repository);
        user.setName("Ada");
        user.setEmail("ada@example.com");
        for (Role role : Role.values()) user.revokeRole(role);
        user.grantRole(Role.STUDENT);
        when(repository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(repository.findAll()).thenReturn(List.of(user));
        when(repository.findById(2L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
    }

    @AfterAll
    void stop() { if (context != null) context.close(); }

    private String loginBody(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ada@example.com", "ValidPass123!")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject("2").issuedAt(NOW.minusSeconds(10)).expiresAt(NOW.plusSeconds(900));
    }

    private String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    private void assertInvalid(String token) throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        verify(repository, never()).findAll();
    }

    @Test
    void loginProducesValidMinimalClaimsAndUnlocksProtectedApi() throws Exception {
        String token = login();
        Jwt jwt = decoder.decode(token);
        assertEquals("2", jwt.getSubject());
        assertEquals(JwtConfig.ISSUER, jwt.getIssuer().toString());
        assertEquals(List.of(JwtConfig.AUDIENCE), jwt.getAudience());
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plusSeconds(900), jwt.getExpiresAt());
        assertEquals(Set.of("iss", "sub", "aud", "iat", "exp", "jti"), jwt.getClaims().keySet());
        mvc.perform(get("/api/users/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("2"));
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongUnknownAndLegacyCredentialsHaveIdenticalResponses() throws Exception {
        User legacy = new User("Legacy", "legacy@example.com");
        ReflectionTestUtils.setField(legacy, "id", 1L);
        when(repository.findByEmail("legacy@example.com")).thenReturn(Optional.of(legacy));
        Set<String> bodies = new HashSet<>();
        for (String email : List.of("ada@example.com", "unknown@example.com", "legacy@example.com")) {
            var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "WrongPass123!")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("E-posta veya parola hatalı."))
                    .andExpect(jsonPath("$.accessToken").doesNotExist()).andReturn();
            bodies.add(result.getResponse().getContentAsString());
        }
        assertEquals(1, bodies.size());
        assertNull(legacy.getPasswordHash());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "exact-expiry", "future-nbf", "future-iat", "wrong-issuer",
            "wrong-audience", "missing-audience", "missing-expiry", "missing-issued", "missing-subject", "invalid-subject"})
    void rejectsInvalidClaims(String scenario) throws Exception {
        var builder = claims();
        switch (scenario) {
            case "expired" -> builder.issuedAt(NOW.minusSeconds(2000)).expiresAt(NOW.minusSeconds(1));
            case "exact-expiry" -> builder.expiresAt(NOW);
            case "future-nbf" -> builder.notBefore(NOW.plusSeconds(60));
            case "future-iat" -> builder.issuedAt(NOW.plusSeconds(60));
            case "wrong-issuer" -> builder.issuer("other-app");
            case "wrong-audience" -> builder.audience(List.of("other-api"));
            case "missing-audience" -> builder.claims(map -> map.remove("aud"));
            case "missing-expiry" -> builder.claims(map -> map.remove("exp"));
            case "missing-issued" -> builder.claims(map -> map.remove("iat"));
            case "missing-subject" -> builder.claims(map -> map.remove("sub"));
            case "invalid-subject" -> builder.subject("not-a-user-id");
        }
        assertInvalid(sign(builder.build()));
    }

    @Test
    void rejectsMalformedTokenTamperedSignatureAndUnsignedJwt() throws Exception {
        assertInvalid("not-a-jwt");
        String token = sign(claims().build());
        String[] parts = token.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        assertInvalid(parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
        String none = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertInvalid(none + "." + parts[1] + ".");
    }

    @Test
    void insufficientAuthorityIs403AndAnonymousIs401() throws Exception {
        mvc.perform(get("/test/admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/test/admin").header("Authorization", "Bearer " + login()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void sessionCookiesAndBasicAuthDoNotAuthenticate() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Basic YWRhOnBhc3M="))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "unused")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "é"})
    void invalidPasswordLengthIsHandled(String password) throws Exception {
        String value = password.equals("é") ? password.repeat(37) : password;
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ada@example.com", value))).andReturn();
        assertEquals(password.equals("short") ? 401 : 400, result.getResponse().getStatus());
        if (!password.equals("short")) verifyNoInteractions(repository);
    }

    @Test
    void requestAndResponseToStringNeverExposeCredentials() {
        assertFalse(new LoginRequest("ada@example.com", "PrivatePass!").toString().contains("PrivatePass!"));
        assertFalse(new LoginResponse("private-token", "Bearer", 900).toString().contains("private-token"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "CLUB_ADMIN"})
    void nonAdminsCanReadAndUpdateOnlyTheirOwnProfile(Role role) throws Exception {
        user.revokeRole(Role.STUDENT);
        user.grantRole(role);
        String token = login();
        mvc.perform(get("/api/users/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(put("/api/users/2").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/3").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/3").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isForbidden());
        verify(repository, never()).findById(3L);
        verify(repository, never()).existsByEmailAndIdNot("other@example.com", 3L);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "CLUB_ADMIN"})
    void nonAdminsCannotListCreateOrDeleteUsers(Role role) throws Exception {
        user.revokeRole(Role.STUDENT);
        user.grantRole(role);
        String token = login();
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verify(repository, never()).findAll();
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void adminCanManageUsersWithoutBeingTheOwner() throws Exception {
        user.revokeRole(Role.STUDENT);
        user.grantRole(Role.ADMIN);
        User other = new User("Other", "other@example.com");
        ReflectionTestUtils.setField(other, "id", 3L);
        when(repository.findById(3L)).thenReturn(Optional.of(other));
        String token = login();
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/users/3").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(put("/api/users/3").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Managed\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/users/3").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        verify(repository).delete(other);
    }

    @Test
    void roleRevocationAffectsTheSameTokenImmediately() throws Exception {
        String token = login();
        user.grantRole(Role.ADMIN);
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        user.revokeRole(Role.ADMIN);
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        user.revokeRole(Role.STUDENT);
        mvc.perform(get("/api/users/2").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountCannotUseAnExistingToken() throws Exception {
        String token = login();
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertInvalid(token);
    }

    @Test
    void tokenRoleClaimsDoNotGrantPlatformPrivileges() throws Exception {
        String token = sign(claims().claim("roles", List.of("ADMIN")).claim("scope", "ADMIN").build());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void numericSubjectOverflowReturns401InsteadOf500() throws Exception {
        assertInvalid(sign(claims().subject("9".repeat(100)).build()));
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void requestPayloadCannotPromoteAnExistingStudent() throws Exception {
        mvc.perform(put("/api/users/2").header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk());
        assertEquals(Set.of(Role.STUDENT), user.getRoles());
    }

    @Test
    void publicRegisterCannotChooseAnElevatedRole() throws Exception {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertEquals(Set.of(Role.STUDENT), saved.getRoles());
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"email\":\"new@example.com\",\"password\":\"ValidPass123!\",\"roles\":[\"ADMIN\",\"CLUB_ADMIN\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles").doesNotExist());
        verify(repository).saveAndFlush(any(User.class));
    }

    @Test
    void directServiceCallsStillEnforceRoleAndOwnership() {
        var contextHolder = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var service = context.getBean(UserService.class);
        try {
            for (Role role : List.of(Role.STUDENT, Role.CLUB_ADMIN)) {
                var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "2", "unused", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
                contextHolder.setAuthentication(authentication);
                assertEquals(2L, service.getUserById(2L).id());
                assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getUserById(3L));
                assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.updateUser(3L,
                        new com.campusplus.backend.dto.UpdateUserRequest("Forbidden", "other@example.com")));
                assertThrows(org.springframework.security.access.AccessDeniedException.class, service::getAllUsers);
                assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.saveUser(
                        new com.campusplus.backend.dto.CreateUserRequest("Forbidden", "other@example.com")));
                assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.deleteUser(2L));
            }
            verify(repository, never()).findById(3L);
            verify(repository, never()).save(any());
            verify(repository, never()).delete(any());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void invalidKeyConfigurationFailsWithoutEchoingSecret() {
        var config = new JwtConfig();
        for (String bad : List.of("not-base64-secret", Base64.getEncoder().encodeToString(new byte[8]))) {
            var environment = new org.springframework.mock.env.MockEnvironment().withProperty("JWT_SECRET", bad);
            var exception = assertThrows(IllegalStateException.class, () -> config.jwtSigningKey(environment));
            assertFalse(exception.getMessage().contains(bad));
            assertNull(exception.getCause());
        }
        assertThrows(IllegalStateException.class,
                () -> config.jwtSigningKey(new org.springframework.mock.env.MockEnvironment()));
    }
}
