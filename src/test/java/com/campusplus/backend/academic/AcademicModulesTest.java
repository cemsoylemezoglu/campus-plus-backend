package com.campusplus.backend.academic;

import com.campusplus.backend.config.SecurityConfig;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.academic.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.*;
import jakarta.servlet.Filter;
import jakarta.validation.ConstraintViolationException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

// Real security, JWT, MVC, method validation and service beans; repositories only are mocked.
// No DataSource or Hibernate context is created; migration approval is not bypassed.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcademicModulesTest {
    private AnnotationConfigWebApplicationContext context;
    private UserRepository users;
    private UniversityRepository universities;
    private StudentProfileRepository profiles;
    private University university;
    private StudentProfile profile;
    private User student;
    private MockMvc mvc;
    private JwtEncoder encoder;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String UNIVERSITY = "/api/universities";
    private static final String OWN = "/api/me/student-profile";
    private static final String ADMIN_PROFILE = "/api/admin/users/2/student-profile";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, CurrentActor.class, UniversityService.class, StudentProfileService.class,
            UniversityController.class, StudentProfileController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UniversityRepository universities() { return mock(UniversityRepository.class); }
        @Bean StudentProfileRepository profiles() { return mock(StudentProfileRepository.class); }
        @Bean @Primary Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean static MethodValidationPostProcessor methodValidation() {
            var processor = new MethodValidationPostProcessor();
            processor.setProxyTargetClass(true);
            return processor;
        }
    }

    @BeforeAll
    void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "JWT_SECRET=" + JwtTestKeys.newSecret());
        context.register(Config.class); context.refresh();
        users = context.getBean(UserRepository.class);
        universities = context.getBean(UniversityRepository.class);
        profiles = context.getBean(StudentProfileRepository.class);
        encoder = context.getBean(JwtEncoder.class);
        mvc = webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterAll void stop() { if (context != null) context.close(); }
    @BeforeEach
    void fixtures() {
        reset(users, universities, profiles);
        student = new User("Student", "student@example.com");
        ReflectionTestUtils.setField(student, "id", 2L); student.setPasswordHash("synthetic-test-hash");
        User admin = new User("Admin", "admin@example.com");
        ReflectionTestUtils.setField(admin, "id", 3L); admin.setPasswordHash("synthetic-test-hash");
        admin.revokeRole(Role.STUDENT); admin.grantRole(Role.ADMIN);
        when(users.findById(2L)).thenReturn(Optional.of(student));
        when(users.findById(3L)).thenReturn(Optional.of(admin));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(student));
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(admin));
        university = new University("UNI", "University", "Istanbul", "TR", "https://example.com", NOW);
        ReflectionTestUtils.setField(university, "id", 10L); ReflectionTestUtils.setField(university, "version", 0L);
        when(universities.findById(10L)).thenReturn(Optional.of(university));
        when(universities.findByIdForShare(10L)).thenReturn(Optional.of(university));
        when(universities.findByIdForUpdate(10L)).thenReturn(Optional.of(university));
        when(universities.search(anyString(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(university), PageRequest.of(0, 20), 1));
        when(universities.saveAndFlush(any())).thenAnswer(i -> {
            University u = i.getArgument(0);
            if (u.getId() == null) ReflectionTestUtils.setField(u, "id", 11L);
            ReflectionTestUtils.setField(u, "version", u.getVersion() == null ? 0L : u.getVersion() + 1);
            return u;
        });
        profile = new StudentProfile(student, university, "Computer Science", (short) 2, "Private bio", NOW);
        ReflectionTestUtils.setField(profile, "version", 0L);
        when(profiles.saveAndFlush(any())).thenAnswer(i -> {
            StudentProfile p = i.getArgument(0);
            ReflectionTestUtils.setField(p, "version", p.getVersion() == null ? 0L : p.getVersion() + 1);
            return p;
        });
    }
    private void existingProfile() {
        when(profiles.findById(2L)).thenReturn(Optional.of(profile));
        when(profiles.existsById(2L)).thenReturn(true);
    }
    private String bearer(long id) {
        var claims = JwtClaimsSet.builder().issuer(com.campusplus.backend.config.JwtConfig.ISSUER)
                .audience(List.of(com.campusplus.backend.config.JwtConfig.AUDIENCE)).subject(Long.toString(id))
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    private String body(Object... pairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) data.put((String) pairs[i], pairs[i + 1]);
        return json.writeValueAsString(data);
    }
    private String universityBody() { return body("code", " uni ", "name", " New University ", "city", " Istanbul ", "countryCode", "tr"); }
    private String profileBody() { return body("universityId", 10, "department", " Engineering ", "studyYear", 2, "bio", "Bio"); }
    private String updateProfileBody(long version) { return body("universityId", 10, "department", "New Department", "studyYear", null, "bio", null, "version", version); }

    @Test
    void anonymousCannotReadOrCreateAcademicResources() throws Exception {
        mvc.perform(get(UNIVERSITY)).andExpect(status().isUnauthorized());
        mvc.perform(get(OWN)).andExpect(status().isUnauthorized());
        mvc.perform(post(OWN).contentType(MediaType.APPLICATION_JSON).content(profileBody())).andExpect(status().isUnauthorized());
        verifyNoInteractions(universities, profiles);
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"STUDENT", "CLUB_ADMIN"})
    void nonAdminsCannotManageUniversitiesOrReadOtherProfiles(Role role) throws Exception {
        student.revokeRole(Role.STUDENT); student.grantRole(role);
        mvc.perform(post(UNIVERSITY).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(universityBody())).andExpect(status().isForbidden());
        mvc.perform(patch(UNIVERSITY + "/10").header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("active", false, "version", 0))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_PROFILE).header("Authorization", bearer(2))).andExpect(status().isForbidden());
        mvc.perform(put(ADMIN_PROFILE).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileBody(0))).andExpect(status().isForbidden());
        verifyNoInteractions(universities, profiles);
    }
    @Test
    void adminCreatesCanonicalUniversityWithLocationAndNoCredentials() throws Exception {
        mvc.perform(post(UNIVERSITY).header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(universityBody()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/universities/11"))
                .andExpect(jsonPath("$.code").value("UNI")).andExpect(jsonPath("$.name").value("New University"))
                .andExpect(jsonPath("$.countryCode").value("TR")).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(universities).existsByCode("UNI");
    }
    @Test
    void duplicateUniversityCodeIs409AndDoesNotSave() throws Exception {
        when(universities.existsByCode("UNI")).thenReturn(true);
        mvc.perform(post(UNIVERSITY).header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(universityBody())).andExpect(status().isConflict());
        verify(universities, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings = {"ftp://example.com", "javascript:alert(1)", "file:///etc/hosts", "https://user:pass@example.com", "not a URL"})
    void rejectsUnsafeWebsiteSchemesOrCredentials(String url) throws Exception {
        mvc.perform(post(UNIVERSITY).header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("code", "UNI", "name", "University", "city", "City", "countryCode", "TR", "websiteUrl", url)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(universities);
    }
    @ParameterizedTest @ValueSource(strings = {"ZZ", "TUR", "1X", ""})
    void rejectsInvalidCountryCodes(String code) throws Exception {
        mvc.perform(post(UNIVERSITY).header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("code", "UNI", "name", "University", "city", "City", "countryCode", code)))
                .andExpect(status().isBadRequest());
    }
    @Test
    void catalogSearchEscapesLikeWildcardsAndUsesStablePagination() throws Exception {
        mvc.perform(get(UNIVERSITY).param("query", "%_!").header("Authorization", bearer(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(universities).search(eq("%!%!_!!%"), eq(false), captor.capture());
        assertEquals(Sort.by("id").ascending(), captor.getValue().getSort());
    }
    @ParameterizedTest @ValueSource(strings = {"size=0", "size=101", "page=-1"})
    void rejectsInvalidPageBounds(String parameter) throws Exception {
        String[] pair = parameter.split("=");
        mvc.perform(get(UNIVERSITY).param(pair[0], pair[1]).header("Authorization", bearer(2))).andExpect(status().isBadRequest());
        verifyNoInteractions(universities);
    }
    @Test
    void inactiveUniversitiesAreHiddenExceptForAdmin() throws Exception {
        university.update(university.getName(), university.getCity(), null, false, NOW);
        mvc.perform(get(UNIVERSITY + "/10").header("Authorization", bearer(2))).andExpect(status().isNotFound());
        mvc.perform(get(UNIVERSITY + "/10").header("Authorization", bearer(3))).andExpect(status().isOk());
        mvc.perform(get(UNIVERSITY).param("includeInactive", "true").header("Authorization", bearer(2))).andExpect(status().isForbidden());
        mvc.perform(get(UNIVERSITY).param("includeInactive", "true").header("Authorization", bearer(3))).andExpect(status().isOk());
    }
    @Test
    void missingUniversityReturns404() throws Exception {
        mvc.perform(get(UNIVERSITY + "/999").header("Authorization", bearer(2))).andExpect(status().isNotFound());
    }
    @Test
    void adminPatchPreservesImmutableFieldsAndCanClearWebsite() throws Exception {
        mvc.perform(patch(UNIVERSITY + "/10").header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Updated", "websiteUrl", "", "active", false, "version", 0, "code", "HACKED", "countryCode", "US")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("UNI"))
                .andExpect(jsonPath("$.countryCode").value("TR")).andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.websiteUrl").isEmpty()).andExpect(jsonPath("$.active").value(false));
    }
    @Test
    void absentWebsiteInPatchPreservesIt() throws Exception {
        mvc.perform(patch(UNIVERSITY + "/10").header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("city", "Ankara", "version", 0))).andExpect(status().isOk());
        assertEquals("https://example.com", university.getWebsiteUrl());
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "blank", "missing-version"})
    void patchRequiresChangesNonBlankFieldsAndVersion(String kind) throws Exception {
        String data = switch (kind) {
            case "empty" -> body("version", 0);
            case "blank" -> body("name", " ", "version", 0);
            default -> body("city", "City");
        };
        mvc.perform(patch(UNIVERSITY + "/10").header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(data)).andExpect(status().isBadRequest());
        verifyNoInteractions(universities);
    }
    @Test
    void staleUniversityVersionDoesNotMutateOrSave() throws Exception {
        mvc.perform(patch(UNIVERSITY + "/10").header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Stale", "version", 5))).andExpect(status().isConflict());
        assertEquals("University", university.getName());
        verify(universities, never()).saveAndFlush(any());
    }
    @Test
    void existingAccountWithoutStudentProfileGets404AndCanCreateItsOwn() throws Exception {
        mvc.perform(get(OWN).header("Authorization", bearer(2))).andExpect(status().isNotFound());
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("userId", 3, "universityId", 10, "department", " Engineering ", "roles", List.of("ADMIN"))))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(2)).andExpect(jsonPath("$.department").value("Engineering"))
                .andExpect(jsonPath("$.roles").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.user").doesNotExist());
        verify(users).findByIdForUpdate(2L);
        verify(users, never()).findByIdForUpdate(3L);
        assertEquals(Set.of(Role.STUDENT), student.getRoles());
        assertEquals("synthetic-test-hash", student.getPasswordHash());
        verify(users, never()).save(any());
    }
    @Test
    void duplicateProfileIs409() throws Exception {
        existingProfile();
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody())).andExpect(status().isConflict());
        verify(profiles, never()).saveAndFlush(any());
    }
    @Test
    void cannotCreateAtInactiveUniversity() throws Exception {
        university.update("University", "City", null, false, NOW);
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody())).andExpect(status().isConflict());
        verify(profiles, never()).saveAndFlush(any());
    }
    @Test
    void missingUniversityOrAccountDoesNotCreateProfile() throws Exception {
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("universityId", 999, "department", "Engineering"))).andExpect(status().isNotFound());
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody())).andExpect(status().isNotFound());
        verify(profiles, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(ints = {-1, 9})
    void rejectsStudyYearsOutsideRange(int year) throws Exception {
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("universityId", 10, "department", "Engineering", "studyYear", year)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles, universities);
    }
    @ParameterizedTest @ValueSource(strings = {"department", "bio"})
    void rejectsOverlongAcademicFields(String field) throws Exception {
        Map<String, Object> data = new HashMap<>(Map.of("universityId", 10, "department", "Engineering"));
        data.put(field, "x".repeat(field.equals("department") ? 151 : 1001));
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(data))).andExpect(status().isBadRequest());
        verifyNoInteractions(profiles, universities);
    }
    @Test
    void profileIsPrivateAndOnlySelfOrAdminMayReadIt() throws Exception {
        existingProfile();
        mvc.perform(get(OWN).header("Authorization", bearer(2))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.bio").value("Private bio"));
        mvc.perform(get(ADMIN_PROFILE).header("Authorization", bearer(3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2));
    }
    @Test
    void selfUpdateAllowsNullOptionalsAndKeepingAnInactiveUniversity() throws Exception {
        existingProfile(); university.update("University", "City", null, false, NOW);
        mvc.perform(put(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileBody(0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").isEmpty()).andExpect(jsonPath("$.studyYear").isEmpty())
                .andExpect(jsonPath("$.version").value(1));
        assertEquals("New Department", profile.getDepartment());
    }
    @Test
    void staleProfileVersionDoesNotMutateOrSave() throws Exception {
        existingProfile();
        mvc.perform(put(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileBody(99))).andExpect(status().isConflict());
        assertEquals("Computer Science", profile.getDepartment());
        verify(profiles, never()).saveAndFlush(any());
    }
    @Test
    void adminCanUpdateProfileButCannotBypassVersionOrUniversityRules() throws Exception {
        existingProfile();
        mvc.perform(put(ADMIN_PROFILE).header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileBody(0))).andExpect(status().isOk());
        mvc.perform(put(ADMIN_PROFILE).header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileBody(0))).andExpect(status().isConflict());
    }
    @Test
    void profileUniversityChangeIsActiveOnlyAndSerializedByUserLock() throws Exception {
        existingProfile();
        University target = new University("OTHER", "Other", "City", "TR", null, NOW);
        ReflectionTestUtils.setField(target, "id", 20L); ReflectionTestUtils.setField(target, "version", 0L);
        target.update("Other", "City", null, false, NOW);
        when(universities.findByIdForShare(20L)).thenReturn(Optional.of(target));
        mvc.perform(put(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("universityId", 20, "department", "Engineering", "version", 0))).andExpect(status().isConflict());
        target.update("Other", "City", null, true, NOW);
        mvc.perform(put(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("universityId", 20, "department", "Engineering", "version", 0))).andExpect(status().isOk());
        var order = inOrder(users, profiles, universities);
        order.verify(users).findById(2L); // JWT account lookup.
        order.verify(users).findByIdForUpdate(2L);
        order.verify(profiles).findById(2L);
        order.verify(universities).findByIdForShare(20L);
    }
    @Test
    void databaseRaceAndLockConflictsDoNotExposeDetails() throws Exception {
        doThrow(new OptimisticLockingFailureException("private SQL")).when(universities).saveAndFlush(any());
        mvc.perform(patch(UNIVERSITY + "/10").header("Authorization", bearer(3)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Updated", "version", 0))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Kayıt değişti veya işlem şu anda kilitli; güncel veriyi alıp tekrar deneyiniz."));
        when(users.findByIdForUpdate(2L)).thenThrow(new CannotAcquireLockException("private SQL"));
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody())).andExpect(status().isConflict());
    }
    @Test
    void profileUniquenessRaceReturns409() throws Exception {
        doThrow(new DataIntegrityViolationException("private SQL")).when(profiles).saveAndFlush(any());
        mvc.perform(post(OWN).header("Authorization", bearer(2)).contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody())).andExpect(status().isConflict());
    }
    @Test
    void directServiceCallsEnforceRolesAndValidation() {
        var security = SecurityContextHolder.getContext();
        try {
            security.setAuthentication(new UsernamePasswordAuthenticationToken("2", "unused",
                    List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
            var universityService = context.getBean(UniversityService.class);
            var profileService = context.getBean(StudentProfileService.class);
            assertThrows(AccessDeniedException.class, () -> universityService.create(new CreateUniversityRequest("UNI", "Name", "City", "TR", null)));
            assertThrows(AccessDeniedException.class, () -> profileService.getForAdmin(3L));
            assertThrows(AccessDeniedException.class, () -> profileService.updateForAdmin(3L,
                    new UpdateStudentProfileRequest(10L, "Department", null, null, 0L)));
            assertThrows(ConstraintViolationException.class, () -> universityService.list("", -1, 20, false));
            assertThrows(ConstraintViolationException.class, () -> profileService.createOwn(
                    new CreateStudentProfileRequest(10L, " ", (short) 9, null)));
            verifyNoInteractions(profiles, universities);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
