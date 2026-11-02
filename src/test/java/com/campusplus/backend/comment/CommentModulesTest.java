package com.campusplus.backend.comment;

import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.post.*;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.service.comment.*;
import com.campusplus.backend.service.notification.CommentNotificationWriter;
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
class CommentModulesTest {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, CurrentActor.class, ClubPolicy.class, ClubMembershipPolicy.class, ClubWorkspace.class,
            PostPolicy.class, PostWorkspace.class, PostService.class, PostQueryService.class, PostModerationService.class,
            PostLifecycleService.class, PostController.class, StudentPostController.class, ClubPostController.class,
            PostModerationController.class, GlobalExceptionHandler.class, CommentPolicy.class, CommentWorkspace.class, CommentService.class, CommentQueryService.class, LikeService.class, CommentModerationService.class, CommentController.class, OwnCommentController.class, LikeController.class, CommentModerationController.class})
    static class Config {
        @Bean PostCommentRepository comments(){return mock(PostCommentRepository.class);}
        @Bean PostLikeRepository likes(){return mock(PostLikeRepository.class);}
        @Bean PostCommentLikeRepository commentLikes(){return mock(PostCommentLikeRepository.class);}
        @Bean CommentModerationRepository commentAudit(){return mock(CommentModerationRepository.class);}
        @Bean CommentNotificationWriter notificationWriter(){return mock(CommentNotificationWriter.class);}

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
    PostCommentRepository comments;PostLikeRepository likes;PostCommentLikeRepository commentLikes;CommentModerationRepository commentAudit;CommentNotificationWriter notificationWriter;
    final Map<Long,PostComment> discussions=new HashMap<>();final Map<String,PostLike> postReactions=new HashMap<>();final Map<String,PostCommentLike> commentReactions=new HashMap<>();
    @BeforeAll void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "JWT_SECRET=" + JwtTestKeys.newSecret());
        context.register(Config.class); context.refresh(); encoder = context.getBean(JwtEncoder.class);
        users = context.getBean(UserRepository.class); universities = context.getBean(UniversityRepository.class);
        profiles = context.getBean(StudentProfileRepository.class); clubs = context.getBean(ClubRepository.class);
        memberships = context.getBean(ClubMembershipRepository.class); posts = context.getBean(PostRepository.class);
        audit = context.getBean(PostModerationRepository.class); em = context.getBean(EntityManager.class); policy = context.getBean(PostPolicy.class);
        comments=context.getBean(PostCommentRepository.class);likes=context.getBean(PostLikeRepository.class);commentLikes=context.getBean(PostCommentLikeRepository.class);commentAudit=context.getBean(CommentModerationRepository.class);notificationWriter=context.getBean(CommentNotificationWriter.class);
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
        reset(users, universities, profiles, clubs, memberships, posts, audit, em,comments,likes,commentLikes,commentAudit,notificationWriter);
        discussions.clear();postReactions.clear();commentReactions.clear();
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
        discussionFixture();
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

    PostComment discussion(long id,long post,long author,Long root) {
        var c=new PostComment(publications.get(post),people.get(author),root==null?null:discussions.get(root),"Private comment "+id,NOW.minusSeconds(30));
        field(c,"id",id);field(c,"version",0L);discussions.put(id,c);return c;
    }
    CommentRow commentRow(PostComment c){return new CommentRow(c.getId(),c.getPost().getId(),c.getRoot()==null?null:c.getRoot().getId(),c.getAuthor().getId(),c.getAuthor().getName(),c.getBody(),c.getStatus(),c.isResourceHidden(),c.isPlatformHidden(),c.getCreatedAt(),c.getUpdatedAt(),c.getRemovedAt(),c.getVersion());}
    boolean sourceVisible(PostComment c,long actor){return policy.visible(c.getPost(),people.get(actor),academics.get(actor),c.getPost().getClub()==null?null:local(c.getPost().getClub().getId(),actor));}
    boolean display(PostComment c){return c.visibleContent()||c.getRoot()==null&&!c.hidden()&&discussions.values().stream().anyMatch(r->r.getRoot()==c&&r.visibleContent());}
    void discussionFixture(){
        discussion(100,51,4,null);discussion(101,51,2,100L);discussion(102,51,3,100L);discussion(110,52,8,null);discussion(120,53,6,null);
        when(comments.findById(anyLong())).thenAnswer(i->Optional.ofNullable(discussions.get(i.<Long>getArgument(0))));
        when(comments.lock(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(discussions.get(i.<Long>getArgument(0))).filter(c->c.getPost().getId().equals(i.getArgument(1))));
        var next=new AtomicLong(200);
        when(comments.saveAndFlush(any())).thenAnswer(i->{PostComment c=i.getArgument(0);if(c.getId()==null){field(c,"id",next.getAndIncrement());field(c,"version",0L);}else field(c,"version",c.getVersion()+1);discussions.put(c.getId(),c);return c;});
        when(comments.visible(anyLong(),anyLong(),anyLong(),anyLong())).thenAnswer(i->{var c=discussions.get(i.<Long>getArgument(1));return c!=null&&c.getPost().getId().equals(i.getArgument(0))&&sourceVisible(c,i.getArgument(2))&&display(c)?Optional.of(commentRow(c)):Optional.empty();});
        when(comments.rootsFirst(anyLong(),anyLong(),anyLong(),any())).thenAnswer(i->discussions.values().stream().filter(c->c.getPost().getId().equals(i.getArgument(0))&&c.getRoot()==null&&display(c)&&sourceVisible(c,i.getArgument(1))).sorted(Comparator.comparing(PostComment::getId)).limit(i.<Pageable>getArgument(3).getPageSize()).map(this::commentRow).toList());
        when(comments.repliesFirst(anyLong(),anyLong(),anyLong(),anyLong(),any())).thenAnswer(i->discussions.values().stream().filter(c->c.getRoot()!=null&&c.getRoot().getId().equals(i.getArgument(1))&&c.visibleContent()).sorted(Comparator.comparing(PostComment::getId)).map(this::commentRow).toList());
        when(comments.replyCounts(any())).thenAnswer(i->i.<Collection<Long>>getArgument(0).stream().map(id->new SourceCount(id,discussions.values().stream().filter(c->c.getRoot()!=null&&c.getRoot().getId().equals(id)&&c.visibleContent()).count())).toList());
        when(comments.own(anyLong(),any())).thenAnswer(i->new PageImpl<>(discussions.values().stream().filter(c->c.getAuthor().getId().equals(i.getArgument(0))).map(this::commentRow).toList()));
        when(comments.managed(nullable(Long.class),nullable(CommentStatus.class),any())).thenReturn(new PageImpl<>(List.of(commentRow(discussions.get(100L)))));
        when(comments.managedDetail(anyLong())).thenAnswer(i->Optional.ofNullable(discussions.get(i.<Long>getArgument(0))).map(this::commentRow));
        when(likes.findByPostIdAndUserId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(postReactions.get(i.getArgument(0)+":"+i.getArgument(1))));
        when(likes.saveAndFlush(any())).thenAnswer(i->{PostLike l=i.getArgument(0);if(l.getId()==null){field(l,"id",next.getAndIncrement());field(l,"version",0L);}postReactions.put(l.getPost().getId()+":"+l.getUser().getId(),l);return l;});
        when(likes.countByPostIdAndActiveTrue(anyLong())).thenAnswer(i->postReactions.values().stream().filter(l->l.isActive()&&l.getPost().getId().equals(i.getArgument(0))).count());
        when(likes.existsByPostIdAndUserIdAndActiveTrue(anyLong(),anyLong())).thenAnswer(i->{var l=postReactions.get(i.getArgument(0)+":"+i.getArgument(1));return l!=null&&l.isActive();});
        when(commentLikes.findByCommentIdAndUserId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(commentReactions.get(i.getArgument(0)+":"+i.getArgument(1))));
        when(commentLikes.saveAndFlush(any())).thenAnswer(i->{PostCommentLike l=i.getArgument(0);if(l.getId()==null){field(l,"id",next.getAndIncrement());field(l,"version",0L);}commentReactions.put(l.getComment().getId()+":"+l.getUser().getId(),l);return l;});
        when(commentAudit.history(anyLong(),nullable(CommentModerationScope.class),any())).thenReturn(Page.empty());
    }
    ResultActions putAs(String path,long id)throws Exception{return mvc.perform(put(path).header("Authorization",token(id)));}
    ResultActions deleteAs(String path,long id)throws Exception{return mvc.perform(delete(path).header("Authorization",token(id)));}
    String commentPath(long post,long id){return "/api/posts/"+post+"/comments/"+id;}
    @ParameterizedTest @ValueSource(strings={"/api/posts/51/comments","/api/posts/51/comments/100","/api/posts/51/interactions","/api/me/comments","/api/admin/comments"})
    void anonymousCannotRead(String path)throws Exception{mvc.perform(get(path)).andExpect(status().isUnauthorized());}
    @Test void newCommentIdentityComesFromActorAndPath()throws Exception{
        postAs("/api/posts/51/comments",4,body("body"," Body ","authorId",5,"postId",53,"rootId",100,"status","REMOVED"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.id").value(200)).andExpect(jsonPath("$.postId").value(51)).andExpect(jsonPath("$.rootId").isEmpty())
            .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.body").value("Body"));
        assertEquals(4,discussions.get(200L).getAuthor().getId());verify(notificationWriter).created(eq(discussions.get(200L)),eq(people.get(4L)));
    }
    @Test void repliesRequireAnActiveRootInSamePost()throws Exception{
        postAs(commentPath(51,100)+"/replies",8,body("body","Reply")).andExpect(status().isCreated()).andExpect(jsonPath("$.rootId").value(100));
        postAs(commentPath(53,100)+"/replies",8,body("body","Cross post")).andExpect(status().isNotFound());
        postAs(commentPath(51,101)+"/replies",8,body("body","Third level")).andExpect(status().isConflict());
        discussions.get(100L).remove(NOW);postAs(commentPath(51,100)+"/replies",8,body("body","Deleted root")).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(longs={2,5,8}) void onlyAuthorCanRewriteComment(long actor)throws Exception{
        patchAs(commentPath(51,100),actor,body("version",0,"body","No")).andExpect(status().isNotFound());
    }
    @Test void authorEditsAndStaleVersionFails()throws Exception{
        patchAs(commentPath(51,100),4,body("version",0,"body","Edited")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        patchAs(commentPath(51,100),4,body("version",0,"body","Stale")).andExpect(status().isConflict());
        verify(notificationWriter,never()).created(any(),any());
    }
    @Test void removedRootKeepsRepliesWithoutAuthorOrText()throws Exception{
        postAs("/api/me/comments/100/remove",4,body("version",0)).andExpect(status().isOk());
        getAs(commentPath(51,100),8).andExpect(status().isOk()).andExpect(jsonPath("$.removed").value(true))
            .andExpect(jsonPath("$.body").isEmpty()).andExpect(jsonPath("$.author").isEmpty()).andExpect(jsonPath("$.replyCount").value(2));
        getAs(commentPath(51,100)+"/replies",8).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        putAs(commentPath(51,100)+"/like",8).andExpect(status().isNotFound());
    }
    @Test void hiddenRootHidesWholeThreadEvenFromAuthor()throws Exception{
        postAs("/api/admin/comments/100/hide",5,body("version",0,"reason","Private moderation" )).andExpect(status().isOk());
        getAs(commentPath(51,100),4).andExpect(status().isNotFound());getAs(commentPath(51,101),2).andExpect(status().isNotFound());
        getAs(commentPath(51,100)+"/replies",8).andExpect(status().isNotFound());
        getAs("/api/me/comments/100",4).andExpect(status().isOk()).andExpect(jsonPath("$.platformHidden").value(true)).andExpect(jsonPath("$.reason").doesNotExist());
    }
    @Test void sourceRestoreCannotOverridePlatformHide()throws Exception{
        postAs(commentPath(51,100)+"/hide",8,body("version",0,"reason","Source")).andExpect(status().isOk());
        postAs("/api/admin/comments/100/hide",5,body("version",1,"reason","Platform")).andExpect(status().isOk());
        postAs(commentPath(51,100)+"/restore",8,body("version",2,"reason","Source restore")).andExpect(status().isOk())
            .andExpect(jsonPath("$.body").doesNotExist()).andExpect(jsonPath("$.author").doesNotExist());
        assertFalse(discussions.get(100L).isResourceHidden());assertTrue(discussions.get(100L).isPlatformHidden());
        getAs(commentPath(51,100),8).andExpect(status().isNotFound());
        postAs("/api/admin/comments/100/restore",5,body("version",3,"reason","Reviewed")).andExpect(status().isOk());
        getAs(commentPath(51,100),8).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(longs={4,6,9}) void localModerationRequiresCorrectClubAuthority(long actor)throws Exception{
        postAs(commentPath(53,120)+"/hide",actor,body("version",0,"reason","No")).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(longs={2,3}) void ownerAndManagerCanModerateOwnClubComments(long actor)throws Exception{
        postAs(commentPath(53,120)+"/hide",actor,body("version",0,"reason","Club moderation")).andExpect(status().isOk());
    }
    @Test void ordinarySourceOwnerCannotReadPlatformAudit()throws Exception{
        getAs(commentPath(51,100)+"/moderation-actions",8).andExpect(status().isOk());verify(commentAudit).history(eq(100L),eq(CommentModerationScope.RESOURCE),any());
        getAs("/api/admin/comments/100/moderation-actions",8).andExpect(status().isForbidden());
        getAs("/api/admin/comments/100/moderation-actions",5).andExpect(status().isOk());verify(commentAudit).history(eq(100L),isNull(),any());
    }
    @Test void authorCanRemoveAfterUniversityLossButCannotEdit()throws Exception{
        academics.put(4L,new StudentProfile(people.get(4L),other,"New",null,null,NOW));
        patchAs(commentPath(51,100),4,body("version",0,"body","No")).andExpect(status().isNotFound());
        getAs("/api/me/comments/100",4).andExpect(status().isOk()).andExpect(jsonPath("$.postTitle").doesNotExist());
        postAs("/api/me/comments/100/remove",4,body("version",0)).andExpect(status().isOk());
    }
    @Test void ownershipAlsoProtectsPrivateHistory()throws Exception{
        getAs("/api/me/comments/100",8).andExpect(status().isNotFound());postAs("/api/me/comments/100/remove",5,body("version",0)).andExpect(status().isNotFound());
        getAs("/api/me/comments",4).andExpect(status().isOk());verify(comments).own(eq(4L),any());
    }
    @ParameterizedTest @ValueSource(longs={7,8,9}) void membershipRequiredForPrivateClubInteraction(long actor)throws Exception{
        postAs("/api/posts/52/comments",actor,body("body","No")).andExpect(status().isNotFound());
        putAs("/api/posts/52/like",actor).andExpect(status().isNotFound());
    }
    @Test void membershipLossBlocksFurtherInteractions()throws Exception{
        postAs("/api/posts/52/comments",4,body("body","Member")).andExpect(status().isCreated());
        field(local(10,4),"status",ClubMembershipStatus.LEFT);
        getAs("/api/posts/52/comments",4).andExpect(status().isNotFound());putAs("/api/posts/52/like",4).andExpect(status().isNotFound());
    }
    @Test void currentProfileIsRecheckedAfterUserLock()throws Exception{
        when(users.findByIdForUpdate(4L)).thenAnswer(i->{academics.put(4L,new StudentProfile(people.get(4L),other,"New",null,null,NOW));return Optional.of(people.get(4L));});
        postAs("/api/posts/51/comments",4,body("body","No")).andExpect(status().isNotFound());verify(comments,never()).saveAndFlush(any());
    }
    @Test void likePutAndDeleteAreIdempotentAndRelikeDoesNotSpam()throws Exception{
        for(int i=0;i<2;i++)putAs("/api/posts/51/like",4).andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true));
        assertEquals(1,postReactions.size());verify(notificationWriter,times(1)).postLiked(any(),eq(people.get(4L)));
        for(int i=0;i<2;i++)deleteAs("/api/posts/51/like",4).andExpect(status().isNoContent());
        assertFalse(postReactions.get("51:4").isActive());putAs("/api/posts/51/like",4).andExpect(status().isOk());
        assertEquals(1,postReactions.size());verify(notificationWriter,times(1)).postLiked(any(),any());
        assertEquals(0,publications.get(51L).getVersion());
    }
    @Test void commentLikeCannotCrossSourcesAndOnlyWithdrawsOwnRow()throws Exception{
        putAs(commentPath(51,100)+"/like",8).andExpect(status().isOk());
        putAs(commentPath(53,100)+"/like",8).andExpect(status().isNotFound());
        deleteAs(commentPath(53,100)+"/like",8).andExpect(status().isNoContent());assertTrue(commentReactions.get("100:8").isActive());
        deleteAs(commentPath(51,100)+"/like",2).andExpect(status().isNoContent());assertTrue(commentReactions.get("100:8").isActive());
        deleteAs(commentPath(51,100)+"/like",8).andExpect(status().isNoContent());assertFalse(commentReactions.get("100:8").isActive());
        putAs(commentPath(51,100)+"/like",8).andExpect(status().isOk());verify(notificationWriter,times(1)).commentLiked(any(),any());
    }
    @Test void ownUnlikeWorksAfterSourceHiddenAndMissingStill204()throws Exception{
        putAs("/api/posts/51/like",4).andExpect(status().isOk());publications.get(51L).moderate(true,NOW);
        deleteAs("/api/posts/51/like",4).andExpect(status().isNoContent());assertFalse(postReactions.get("51:4").isActive());
        deleteAs("/api/posts/9223372036854775807/like",4).andExpect(status().isNoContent());
    }
    @Test void directServiceCallsHaveMethodAndResourceSecurity() {
        authenticate(4);assertThrows(AccessDeniedException.class,()->context.getBean(CommentModerationService.class).platform(100L,true,new PostReasonRequest(0L,"No")));
        assertThrows(ResourceNotFoundException.class,()->context.getBean(CommentService.class).update(51L,101L,new UpdateCommentRequest(0L,"No")));
    }
    @ParameterizedTest @ValueSource(strings={"", " ","\t\n"}) void blankBodyRejected(String value)throws Exception{
        postAs("/api/posts/51/comments",4,body("body",value)).andExpect(status().isBadRequest());
    }
    @Test void limitsInvalidCursorAndStaleModerationAreRejected()throws Exception{
        postAs("/api/posts/51/comments",4,body("body","x".repeat(3001))).andExpect(status().isBadRequest());
        getAs("/api/posts/51/comments?size=101",4).andExpect(status().isBadRequest());getAs("/api/posts/51/comments?cursor=broken",4).andExpect(status().isBadRequest());
        postAs(commentPath(51,100)+"/hide",8,body("version",99,"reason","No")).andExpect(status().isConflict());
        postAs("/api/admin/comments/100/hide",5,body("version",0,"reason"," ")).andExpect(status().isBadRequest());
    }
    @Test void cursorAndCountsUseBoundedBatchQueries()throws Exception{
        discussion(103,51,8,null);
        var result=getAs("/api/posts/51/comments?size=1",4).andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(true)).andReturn();
        String cursor=json.readTree(result.getResponse().getContentAsString()).path("nextCursor").asText();
        var decoded=CommentCursor.decode(cursor,51,0);assertEquals(100,decoded.id());
        when(comments.rootsAfter(eq(51L),anyLong(),anyLong(),any(),anyLong(),any())).thenReturn(List.of(commentRow(discussions.get(103L))));
        getAs("/api/posts/51/comments?size=1&cursor="+cursor,4).andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(false));
        verify(comments).rootsAfter(eq(51L),eq(4L),eq(1L),eq(decoded.time()),eq(100L),eq(PageRequest.of(0,2)));
    }
    @Test void moderationOfRemovedRootDoesNotResurrectBody()throws Exception{
        postAs("/api/me/comments/100/remove",4,body("version",0)).andExpect(status().isOk());
        postAs("/api/admin/comments/100/hide",5,body("version",1,"reason","Thread" )).andExpect(status().isOk());
        postAs("/api/admin/comments/100/restore",5,body("version",2,"reason","Reviewed")).andExpect(status().isOk());
        getAs(commentPath(51,100),8).andExpect(status().isOk()).andExpect(jsonPath("$.body").isEmpty()).andExpect(jsonPath("$.removed").value(true));
    }
}
