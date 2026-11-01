package com.campusplus.backend.post;

import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.post.*;
import jakarta.persistence.*;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real MVC/JWT/services, mocked persistence. SQL filtering and races require PostgresPostIT. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostModulesTest {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, CurrentActor.class, ClubPolicy.class, ClubMembershipPolicy.class, ClubWorkspace.class,
            PostPolicy.class, PostWorkspace.class, PostService.class, PostQueryService.class, PostModerationService.class,
            PostLifecycleService.class, PostController.class, StudentPostController.class, ClubPostController.class,
            PostModerationController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UniversityRepository universities() { return mock(UniversityRepository.class); }
        @Bean StudentProfileRepository profiles() { return mock(StudentProfileRepository.class); }
        @Bean ClubRepository clubs() { return mock(ClubRepository.class); }
        @Bean ClubMembershipRepository memberships() { return mock(ClubMembershipRepository.class); }
        @Bean PostRepository posts() { return mock(PostRepository.class); }
        @Bean PostModerationRepository audit() { return mock(PostModerationRepository.class); }
        @Bean EntityManager em() { return mock(EntityManager.class); }
        @Bean @Primary Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean static MethodValidationPostProcessor validation() { var p = new MethodValidationPostProcessor(); p.setProxyTargetClass(true); return p; }
    }
    AnnotationConfigWebApplicationContext context; MockMvc mvc; JwtEncoder encoder;
    UserRepository users; UniversityRepository universities; StudentProfileRepository profiles;
    ClubRepository clubs; ClubMembershipRepository memberships; PostRepository posts; PostModerationRepository audit;
    EntityManager em; PostPolicy policy;
    final Map<Long, User> people = new HashMap<>(); final Map<Long, StudentProfile> academics = new HashMap<>();
    final Map<Long, Club> catalog = new HashMap<>(); final Map<Long, ClubMembership> roster = new HashMap<>();
    final Map<Long, Post> publications = new HashMap<>();
    final JsonMapper json = JsonMapper.builder().build(); University university, other;
    static void field(Object bean, String name, Object value) { ReflectionTestUtils.setField(bean, name, value); }
    @BeforeAll void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "JWT_SECRET=" + JwtTestKeys.newSecret());
        context.register(Config.class); context.refresh(); encoder = context.getBean(JwtEncoder.class);
        users = context.getBean(UserRepository.class); universities = context.getBean(UniversityRepository.class);
        profiles = context.getBean(StudentProfileRepository.class); clubs = context.getBean(ClubRepository.class);
        memberships = context.getBean(ClubMembershipRepository.class); posts = context.getBean(PostRepository.class);
        audit = context.getBean(PostModerationRepository.class); em = context.getBean(EntityManager.class); policy = context.getBean(PostPolicy.class);
        mvc = webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterAll void close() { if (context != null) context.close(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    ClubMembership local(long clubId, long actorId) { return roster.get(clubId * 100 + actorId); }
    Post publication(long id, Long clubId, boolean published) {
        var p = new Post(university, people.get(clubId == null ? 8L : clubId == 10 ? 2L : 6L),
                clubId == null ? null : catalog.get(clubId), "Body " + id,
                id == 52 ? PostAudience.CLUB_MEMBERS : PostAudience.UNIVERSITY_STUDENTS, NOW.minusSeconds(120));
        field(p, "id", id); field(p, "version", 0L); if (published) p.publish(NOW.minusSeconds(60)); publications.put(id, p); return p;
    }
    PostRow row(Post p) { return new PostRow(p.getId(), p.getUniversity().getId(), p.getUniversity().getName(), p.getCreatedBy().getId(), p.getCreatedBy().getName(),
            p.getClub() == null ? null : p.getClub().getId(), p.getClub() == null ? null : p.getClub().getName(), p.getPublisher(), p.getAudience(), p.getStatus(),
            p.getBody(), p.getPublishedAt(), p.getUpdatedAt(), p.getCreatedAt(), p.getRemovedAt(), p.getRemovedBy() == null ? null : p.getRemovedBy().getId(),
            p.getRemovalReason(), p.isModerationHidden(), p.getVersion()); }
    @BeforeEach void fixture() {
        reset(users, universities, profiles, clubs, memberships, posts, audit, em);
        people.clear(); academics.clear(); catalog.clear(); roster.clear(); publications.clear();
        university = new University("UNI", "University", "City", "TR", null, NOW); field(university, "id", 1L);
        other = new University("OTHER", "Other", "City", "TR", null, NOW); field(other, "id", 2L);
        for (long id = 2; id <= 9; id++) {
            User u = new User("Person " + id, "person" + id + "@example.test"); field(u, "id", id); u.setPasswordHash("synthetic-test-hash");
            if (Set.of(2L, 3L, 6L, 9L).contains(id)) u.grantRole(Role.CLUB_ADMIN);
            if (id == 5) u.grantRole(Role.ADMIN); people.put(id, u);
            academics.put(id, new StudentProfile(u, id == 7 ? other : university, "Engineering", null, null, NOW));
        }
        for (long id : new long[]{10, 20}) {
            Club c = new Club(university, people.get(id == 10 ? 2L : 6L), "Club " + id, "club-" + id, "Description", null, NOW);
            field(c, "id", id); field(c, "version", 0L); field(c, "status", ClubStatus.ACTIVE); catalog.put(id, c);
        }
        for (long id : new long[]{2, 3, 4, 6}) {
            long club = id == 6 ? 20 : 10;
            var m = new ClubMembership(catalog.get(club), academics.get(id), id == 2 || id == 6, NOW);
            field(m, "id", id + 100); field(m, "version", 0L); field(m, "status", ClubMembershipStatus.ACTIVE);
            if (id == 3) field(m, "role", ClubMembershipRole.MANAGER); roster.put(club * 100 + id, m);
        }
        publication(50, null, false); publication(51, null, true); publication(52, 10L, true);
        publication(53, 10L, true); publication(60, 20L, false);
        when(users.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(profiles.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(academics.get(i.<Long>getArgument(0))));
        when(universities.findByIdForShare(anyLong())).thenAnswer(i -> Optional.of(i.<Long>getArgument(0) == 1 ? university : other));
        when(clubs.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.findByIdForShare(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(memberships.findByClubIdAndUserId(anyLong(), anyLong())).thenAnswer(i -> Optional.ofNullable(local(i.getArgument(0), i.getArgument(1))));
        when(posts.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(publications.get(i.<Long>getArgument(0))));
        when(posts.findForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(publications.get(i.<Long>getArgument(0))));
        AtomicLong next = new AtomicLong(70);
        when(posts.saveAndFlush(any())).thenAnswer(i -> {
            Post p = i.getArgument(0); if (p.getId() == null) { field(p, "id", next.getAndIncrement()); field(p, "version", 0L); }
            else field(p, "version", p.getVersion() + 1); publications.put(p.getId(), p); return p;
        });
        when(posts.visible(anyLong(), anyLong(), anyLong())).thenAnswer(i -> {
            Post p = publications.get(i.<Long>getArgument(0)); long user = i.getArgument(1);
            return p != null && policy.visible(p, people.get(user), academics.get(user), p.getClub() == null ? null : local(p.getClub().getId(), user))
                    ? Optional.of(row(p)) : Optional.empty();
        });
        when(posts.feed(anyLong(), anyLong(), nullable(Long.class), nullable(Instant.class), nullable(Long.class), any()))
                .thenReturn(List.of(row(publications.get(53L)), row(publications.get(51L))));
        when(posts.feedFirst(anyLong(), anyLong(), nullable(Long.class), any()))
                .thenReturn(List.of(row(publications.get(53L)), row(publications.get(51L))));
        when(posts.own(anyLong(), nullable(PostStatus.class), any())).thenReturn(new PageImpl<>(List.of(row(publications.get(50L)))));
        when(posts.managed(anyLong(), nullable(PostStatus.class), any())).thenReturn(new PageImpl<>(List.of(row(publications.get(52L)))));
        when(posts.moderation(nullable(PostStatus.class), nullable(Boolean.class), nullable(Long.class), nullable(Long.class), any()))
                .thenReturn(new PageImpl<>(List.of(row(publications.get(50L)))));
        when(audit.history(anyLong(), any())).thenReturn(Page.empty());
        when(posts.lockUnremoved(anyLong())).thenAnswer(i -> publications.values().stream().filter(p -> p.getClub() != null
                && p.getClub().getId().equals(i.getArgument(0)) && p.getStatus() != PostStatus.REMOVED).sorted(Comparator.comparing(Post::getId)).toList());
    }
    String token(long id) { return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE)).subject(Long.toString(id))
                    .issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build())).getTokenValue(); }
    String body(Object... pairs) { Map<String, Object> values = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]); return json.writeValueAsString(values); }
    ResultActions getAs(String path, long id) throws Exception { return mvc.perform(get(path).header("Authorization", token(id))); }
    ResultActions postAs(String path, long id, String body) throws Exception { return mvc.perform(post(path).header("Authorization", token(id)).contentType("application/json").content(body)); }
    ResultActions patchAs(String path, long id, String body) throws Exception { return mvc.perform(patch(path).header("Authorization", token(id)).contentType("application/json").content(body)); }
    void authenticate(long id) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(Long.toString(id), null,
            people.get(id).getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList())); }

    @ParameterizedTest @ValueSource(strings = {"/api/feed", "/api/posts/51", "/api/me/posts", "/api/admin/posts", "/api/clubs/10/posts/manage"})
    void anonymousIsUnauthorized(String path) throws Exception { mvc.perform(get(path)).andExpect(status().isUnauthorized()); }
    @Test void forgedActorUniversityAndStatusAreNotMassAssigned() throws Exception {
        postAs("/api/me/posts", 8, body("body", " Plain text ", "createdBy", 2, "universityId", 2, "clubId", 10, "status", "PUBLISHED"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location", "/api/posts/70")).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.content.post.author.id").value(8)).andExpect(jsonPath("$.content.post.club").isEmpty())
                .andExpect(jsonPath("$.content.post.university.id").value(1)).andExpect(jsonPath("$.content.body").value("Plain text"));
    }
    @ParameterizedTest @ValueSource(longs = {4, 6, 9})
    void memberOtherManagerAndMarkerCannotCreate(long id) throws Exception {
        postAs("/api/clubs/10/posts", id, body("body", "No")).andExpect(status().isForbidden()); verify(posts, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(longs = {2, 3, 5})
    void localManagersAndAdminCanCreateClubDraft(long id) throws Exception {
        postAs("/api/clubs/10/posts", id, body("body", "Club text", "audience", "CLUB_MEMBERS"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content.post.publisher").value("CLUB"))
                .andExpect(jsonPath("$.content.post.author").isEmpty()).andExpect(jsonPath("$.content.post.club.id").value(10));
    }
    @ParameterizedTest @ValueSource(longs = {2, 5, 7})
    void otherUserIncludingAdminCannotRewriteStudent(long id) throws Exception {
        patchAs("/api/me/posts/51", id, body("version", 0, "body", "No")).andExpect(status().isNotFound());
    }
    @Test void studentLifecyclePreservesPublishOrderAndEndsPermanently() throws Exception {
        postAs("/api/me/posts/50/publish", 8, body("version", 0)).andExpect(status().isOk()); Instant first = publications.get(50L).getPublishedAt();
        patchAs("/api/me/posts/50", 8, body("version", 1, "body", "Edited")).andExpect(status().isOk());
        assertEquals(first, publications.get(50L).getPublishedAt());
        postAs("/api/me/posts/50/remove", 8, body("version", 2, "reason", "My choice")).andExpect(status().isOk());
        postAs("/api/me/posts/50/publish", 8, body("version", 3)).andExpect(status().isConflict());
    }
    @Test void staleVersionFailsBeforeAnySave() throws Exception {
        patchAs("/api/me/posts/51", 8, body("version", 99, "body", "Stale")).andExpect(status().isConflict()); verify(posts, never()).saveAndFlush(any());
    }
    @Test void publishedAudienceCannotBeChanged() throws Exception {
        patchAs("/api/clubs/10/posts/53", 2, body("version", 0, "body", "Changed", "audience", "CLUB_MEMBERS")).andExpect(status().isConflict());
        patchAs("/api/me/posts/50", 8, body("version", 0, "body", "Changed", "audience", "CLUB_MEMBERS")).andExpect(status().isBadRequest());
    }
    @Test void oldUniversityContentIsManageableButOnlyRemovable() throws Exception {
        academics.put(8L, new StudentProfile(people.get(8L), other, "New", null, null, NOW));
        getAs("/api/me/posts/51/manage", 8).andExpect(status().isOk());
        patchAs("/api/me/posts/51", 8, body("version", 0, "body", "No")).andExpect(status().isConflict());
        postAs("/api/me/posts/51/remove", 8, body("version", 0, "reason", "Leaving")).andExpect(status().isOk());
    }
    @Test void missingProfileCannotCreateOrReadFeed() throws Exception {
        academics.remove(8L); postAs("/api/me/posts", 8, body("body", "No")).andExpect(status().isConflict());
        getAs("/api/feed", 8).andExpect(status().isConflict()); getAs("/api/posts/51", 8).andExpect(status().isNotFound());
    }
    @Test void currentRoleAndMembershipAreRecheckedAfterLock() throws Exception {
        doAnswer(i -> { people.get(2L).revokeRole(Role.CLUB_ADMIN); return Optional.of(people.get(2L)); }).when(users).findByIdForUpdate(2L);
        postAs("/api/clubs/10/posts", 2, body("body", "No")).andExpect(status().isForbidden());
    }
    @Test void currentProfileIsRecheckedAfterLock() throws Exception {
        doAnswer(i -> { academics.put(8L, new StudentProfile(people.get(8L), other, "New", null, null, NOW)); return Optional.of(people.get(8L)); })
                .when(users).findByIdForUpdate(8L);
        postAs("/api/me/posts/50/publish", 8, body("version", 0)).andExpect(status().isConflict());
    }
    @Test void suspensionAndInactiveUniversityBlockWrites() throws Exception {
        field(catalog.get(10L), "status", ClubStatus.SUSPENDED);
        postAs("/api/clubs/10/posts", 2, body("body", "No")).andExpect(status().isConflict());
        field(university, "active", false); postAs("/api/me/posts", 8, body("body", "No")).andExpect(status().isConflict());
    }
    @Test void wrongClubRouteCannotManagePost() throws Exception {
        patchAs("/api/clubs/10/posts/60", 2, body("version", 0, "body", "No")).andExpect(status().isNotFound());
    }
    @Test void ordinaryAndManagementDetailsAreSeparated() throws Exception {
        getAs("/api/posts/50", 8).andExpect(status().isNotFound()); getAs("/api/me/posts/50/manage", 8).andExpect(status().isOk());
        getAs("/api/posts/51", 8).andExpect(status().isOk()).andExpect(jsonPath("$.removalReason").doesNotExist())
                .andExpect(jsonPath("$.post.author.email").doesNotExist()).andExpect(jsonPath("$.post.author.roles").doesNotExist());
        getAs("/api/posts/52", 8).andExpect(status().isNotFound()); getAs("/api/posts/52", 4).andExpect(status().isOk());
        field(local(10, 4), "status", ClubMembershipStatus.LEFT); getAs("/api/posts/52", 4).andExpect(status().isNotFound());
        getAs("/api/posts/51", 7).andExpect(status().isNotFound());
    }
    @Test void moderatorCannotEditButCanHideWithAudit() throws Exception {
        postAs("/api/admin/posts/51/hide", 5, body("version", 0, "reason", "Private reason")).andExpect(status().isOk());
        getAs("/api/posts/51", 8).andExpect(status().isNotFound());
        getAs("/api/me/posts/51/manage", 8).andExpect(status().isOk()).andExpect(jsonPath("$.moderationHidden").value(true))
                .andExpect(jsonPath("$.moderationReason").doesNotExist());
        var capture = org.mockito.ArgumentCaptor.forClass(PostModeration.class); verify(audit).save(capture.capture());
        assertEquals(1L, capture.getValue().getResultingVersion()); assertEquals(PostModerationAction.HIDE, capture.getValue().getAction());
        assertEquals(5L, capture.getValue().getActor().getId()); verify(audit).flush();
        patchAs("/api/me/posts/51", 8, body("version", 1, "body", "Bypass")).andExpect(status().isConflict());
        postAs("/api/admin/posts/51/restore", 5, body("version", 1, "reason", "Reviewed")).andExpect(status().isOk());
        getAs("/api/posts/51", 8).andExpect(status().isOk());
    }
    @Test void removedHiddenPostCannotBeRestored() throws Exception {
        postAs("/api/admin/posts/51/hide", 5, body("version", 0, "reason", "Reason")).andExpect(status().isOk());
        postAs("/api/me/posts/51/remove", 8, body("version", 1, "reason", "Remove")).andExpect(status().isOk());
        postAs("/api/admin/posts/51/restore", 5, body("version", 2, "reason", "Restore")).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(longs = {2, 4, 8, 9})
    void nonAdminCannotModerate(long id) throws Exception {
        postAs("/api/admin/posts/51/hide", id, body("version", 0, "reason", "No")).andExpect(status().isForbidden());
        getAs("/api/admin/posts", id).andExpect(status().isForbidden()); verify(audit, never()).save(any());
    }
    @Test void serviceSecurityAppliesWithoutController() {
        authenticate(8); assertThrows(AccessDeniedException.class, () -> context.getBean(PostModerationService.class).moderate(51L, true, new PostReasonRequest(0L, "No")));
        assertThrows(AccessDeniedException.class, () -> context.getBean(PostQueryService.class).adminDetail(51L));
        assertThrows(ResourceNotFoundException.class, () -> context.getBean(PostService.class).update(null, 53L, new UpdatePostRequest(0L, "No", null)));
    }
    @Test void feedUsesSizePlusOneAndStableCursor() throws Exception {
        var result = getAs("/api/feed?size=1", 8).andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items.length()").value(1)).andReturn();
        String cursor = json.readTree(result.getResponse().getContentAsString()).path("nextCursor").asText();
        var decoded = FeedCursor.decode(cursor, null); assertEquals(53, decoded.id());
        verify(posts).feedFirst(eq(8L), eq(1L), isNull(), eq(PageRequest.of(0, 2)));
        getAs("/api/feed?size=1&cursor=" + cursor, 8).andExpect(status().isOk());
        verify(posts).feed(eq(8L), eq(1L), isNull(), eq(decoded.time()), eq(53L), eq(PageRequest.of(0, 2)));
    }
    @Test void finalFeedPageHasNoCursor() throws Exception {
        when(posts.feedFirst(anyLong(), anyLong(), nullable(Long.class), any())).thenReturn(List.of());
        getAs("/api/feed", 8).andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.nextCursor").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"?size=0", "?size=101", "?clubId=-1", "?cursor=broken", "?cursor="})
    void invalidFeedRequestsAreRejected(String query) throws Exception { getAs("/api/feed" + query, 8).andExpect(status().isBadRequest()); }
    @ParameterizedTest @ValueSource(strings = {"", " ", "\t\n"})
    void blankContentIsRejected(String value) throws Exception { postAs("/api/me/posts", 8, body("body", value)).andExpect(status().isBadRequest()); }
    @Test void contentAndReasonBoundsAndVersionAreValidated() throws Exception {
        postAs("/api/me/posts", 8, body("body", "x".repeat(10001))).andExpect(status().isBadRequest());
        postAs("/api/me/posts/50/publish", 8, body("version", -1)).andExpect(status().isBadRequest());
        postAs("/api/me/posts/50/remove", 8, body("version", 0, "reason", " ")).andExpect(status().isBadRequest());
        postAs("/api/admin/posts/51/hide", 5, body("version", 0, "reason", "x".repeat(1001))).andExpect(status().isBadRequest());
    }
    @Test void managementListsAreScopedToActorAndClub() throws Exception {
        getAs("/api/me/posts", 8).andExpect(status().isOk()); verify(posts).own(eq(8L), isNull(), any());
        getAs("/api/clubs/10/posts/manage", 3).andExpect(status().isOk()); verify(posts).managed(eq(10L), isNull(), any());
        getAs("/api/clubs/10/posts/manage", 6).andExpect(status().isForbidden());
        getAs("/api/admin/posts/51/moderation-actions", 5).andExpect(status().isOk());
    }
    @Test void clubArchiveRemovesOnlyUnremovedClubPosts() {
        var p = publications.get(52L); p.remove(people.get(2L), "Already removed", NOW);
        context.getBean(PostLifecycleService.class).clubArchived(catalog.get(10L), people.get(2L), NOW);
        assertEquals(0, p.getVersion()); assertEquals(PostStatus.REMOVED, publications.get(53L).getStatus());
        assertEquals(PostStatus.PUBLISHED, publications.get(51L).getStatus()); verify(posts, times(1)).saveAndFlush(any());
    }
}
