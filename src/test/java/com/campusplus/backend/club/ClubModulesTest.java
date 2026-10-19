package com.campusplus.backend.club;

import com.campusplus.backend.config.*;
import com.campusplus.backend.controller.*;
import com.campusplus.backend.dto.club.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.entity.Role;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
import static com.campusplus.backend.entity.ClubMembershipRole.*;
import static com.campusplus.backend.entity.ClubMembershipStatus.*;

// Real JWT/security/MVC/method validation/services; mock repositories and EntityManager.
// No JDBC, migration or real-data write. Transaction rollback/PG locking require later DB approval.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubModulesTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtEncoder encoder;
    private UserRepository users;
    private ClubRepository clubs;
    private ClubMembershipRepository members;
    private StudentProfileRepository profiles;
    private UniversityRepository universities;
    private EntityManager em;
    private final Map<Long,User> people=new HashMap<>();
    private final Map<Long,StudentProfile> academics=new HashMap<>();
    private final Map<Long,Club> catalog=new HashMap<>();
    private final Map<Long,ClubMembership> roster=new HashMap<>();
    private University university;
    private static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    private final JsonMapper json=JsonMapper.builder().build();

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,CurrentActor.class,ClubPolicy.class,ClubMembershipPolicy.class,ClubWorkspace.class,
            ClubService.class,ClubQueryService.class,ClubMembershipService.class,ClubSettings.class,
            ClubController.class,ClubMembershipController.class,GlobalExceptionHandler.class,StudentProfilePolicy.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ClubRepository clubs() { return mock(ClubRepository.class); }
        @Bean ClubMembershipRepository members() { return mock(ClubMembershipRepository.class); }
        @Bean StudentProfileRepository profiles() { return mock(StudentProfileRepository.class); }
        @Bean UniversityRepository universities() { return mock(UniversityRepository.class); }
        @Bean EntityManager entityManager() { return mock(EntityManager.class); }
        @Bean @Primary Clock clock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
        @Bean static MethodValidationPostProcessor validation() {
            var p=new MethodValidationPostProcessor(); p.setProxyTargetClass(true); return p;
        }
    }
    @BeforeAll void start() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"JWT_SECRET="+JwtTestKeys.newSecret());
        context.register(Config.class); context.refresh();
        encoder=context.getBean(JwtEncoder.class); users=context.getBean(UserRepository.class);
        clubs=context.getBean(ClubRepository.class); members=context.getBean(ClubMembershipRepository.class);
        profiles=context.getBean(StudentProfileRepository.class); universities=context.getBean(UniversityRepository.class);
        em=context.getBean(EntityManager.class);
        mvc=webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
    }
    @AfterAll void close() { if(context!=null) context.close(); }
    private static void field(Object o,String name,Object value) { ReflectionTestUtils.setField(o,name,value); }
    private User person(long id,Role... roles) {
        var u=new User("Person "+id,"person"+id+"@example.com"); field(u,"id",id); u.setPasswordHash("synthetic-hash");
        u.revokeRole(Role.STUDENT); for(Role role:roles) u.grantRole(role); people.put(id,u); return u;
    }
    private ClubMembership membership(long id,long clubId,long userId,ClubMembershipRole role,ClubMembershipStatus status) {
        var m=new ClubMembership(catalog.get(clubId),academics.get(userId),role==OWNER,NOW.minusSeconds(120));
        field(m,"id",id); field(m,"version",0L); field(m,"status",status); field(m,"role",role);
        if(status==ACTIVE) field(m,"joinedAt",NOW.minusSeconds(120));
        roster.put(id,m); return m;
    }
    private <T> Page<T> page(List<T> list,Pageable pageable) {
        int from=(int)Math.min(pageable.getOffset(),list.size()), to=Math.min(from+pageable.getPageSize(),list.size());
        return new PageImpl<>(list.subList(from,to),pageable,list.size());
    }
    @BeforeEach void fixtures() {
        reset(users,clubs,members,profiles,universities,em); people.clear(); academics.clear(); catalog.clear(); roster.clear();
        person(2,Role.STUDENT,Role.CLUB_ADMIN); person(3,Role.STUDENT,Role.CLUB_ADMIN);
        person(4,Role.STUDENT); person(5,Role.ADMIN); person(6,Role.STUDENT,Role.CLUB_ADMIN);
        person(7,Role.STUDENT); person(8,Role.STUDENT);
        university=new University("UNI","University","City","TR",null,NOW); field(university,"id",1L);field(university,"version",0L);
        var other=new University("OTHER","Other University","City","TR",null,NOW);field(other,"id",2L);field(other,"version",0L);
        for(var p:people.values()) {
            var profile=new StudentProfile(p,p.getId()==7?other:university,"Department",(short)2,"private bio",NOW);
            field(profile,"version",0L);academics.put(p.getId(),profile);
        }
        for(long id:new long[]{10,20}) {
            var club=new Club(university,people.get(id==10?2L:6L),"Club "+id,"club-"+id,"Description",null,NOW);
            field(club,"id",id);field(club,"version",0L);field(club,"status",ClubStatus.ACTIVE);catalog.put(id,club);
        }
        membership(100,10,2,OWNER,ACTIVE);membership(101,10,3,MANAGER,ACTIVE);
        membership(102,10,4,MEMBER,ACTIVE);membership(103,10,8,MEMBER,PENDING);membership(200,20,6,OWNER,ACTIVE);
        when(users.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(people.get(i.<Long>getArgument(0))));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(profiles.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(academics.get(i.<Long>getArgument(0))));
        when(universities.findByIdForShare(anyLong())).thenAnswer(i -> Optional.ofNullable(i.<Long>getArgument(0)==1?university:other));
        when(clubs.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        when(clubs.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<Long>getArgument(0))));
        AtomicLong nextClub=new AtomicLong(30),nextMember=new AtomicLong(500);
        when(clubs.saveAndFlush(any(Club.class))).thenAnswer(i -> {
            Club c=i.getArgument(0); if(c.getId()==null) field(c,"id",nextClub.getAndIncrement());
            field(c,"version",c.getVersion()==null?0L:c.getVersion()+1);catalog.put(c.getId(),c); return c;
        });
        when(members.saveAndFlush(any(ClubMembership.class))).thenAnswer(i -> {
            ClubMembership m=i.getArgument(0); if(m.getId()==null)field(m,"id",nextMember.getAndIncrement());
            field(m,"version",m.getVersion()==null?0L:m.getVersion()+1);roster.put(m.getId(),m);return m;
        });
        when(members.findByClubIdAndUserId(anyLong(),anyLong())).thenAnswer(i -> roster.values().stream()
                .filter(m -> m.getClub().getId().equals(i.getArgument(0))&&m.getUserId().equals(i.getArgument(1))).findFirst());
        when(members.findByIdAndClubId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(roster.get(i.<Long>getArgument(0)))
                .filter(m -> m.getClub().getId().equals(i.getArgument(1))));
        when(members.activeOwner(anyLong())).thenAnswer(i -> roster.values().stream()
                .filter(m -> m.getClub().getId().equals(i.getArgument(0))&&m.getStatus()==ACTIVE&&m.getRole()==OWNER).findFirst());
        when(members.activeManagerUserIds(anyLong())).thenAnswer(i -> roster.values().stream()
                .filter(m -> m.getClub().getId().equals(i.getArgument(0))&&m.getStatus()==ACTIVE&&m.getRole()!=MEMBER)
                .map(ClubMembership::getUserId).sorted().toList());
        when(members.hasManagement(anyLong())).thenAnswer(i -> roster.values().stream()
                .anyMatch(m -> m.getUserId().equals(i.getArgument(0))&&m.getStatus()==ACTIVE&&m.getRole()!=MEMBER&&m.getClub().getStatus()!=ClubStatus.ARCHIVED));
        when(members.countByClubIdAndStatus(anyLong(),any())).thenAnswer(i -> roster.values().stream()
                .filter(m -> m.getClub().getId().equals(i.getArgument(0))&&m.getStatus()==i.getArgument(1)).count());
        when(members.counts(anyCollection())).thenAnswer(i -> {
            Collection<Long> ids=i.getArgument(0);
            return ids.stream().map(id -> new ClubMembershipRepository.MemberCount() {
                public Long getClubId() { return id; }
                public long getAmount() { return roster.values().stream().filter(m -> m.getClub().getId().equals(id)&&m.getStatus()==ACTIVE).count(); }
            }).toList();
        });
        when(clubs.search(any(),any(),anyString(),any())).thenAnswer(i -> page(catalog.values().stream()
                .filter(c -> (i.getArgument(0)==null||c.getUniversity().getId().equals(i.getArgument(0)))
                        &&(i.getArgument(1)==null||c.getStatus()==i.getArgument(1))).sorted(Comparator.comparing(Club::getId)).toList(),i.getArgument(3)));
        when(members.own(anyLong(),any(),any())).thenAnswer(i -> page(roster.values().stream()
                .filter(m -> m.getUserId().equals(i.getArgument(0))&&(i.getArgument(1)==null||m.getStatus()==i.getArgument(1)))
                .sorted(Comparator.comparing(ClubMembership::getId)).toList(),i.getArgument(2)));
        when(members.managed(anyLong(),any(),any())).thenAnswer(i -> page(roster.values().stream()
                .filter(m -> m.getClub().getId().equals(i.getArgument(0))&&(i.getArgument(1)==null||m.getStatus()==i.getArgument(1)))
                .sorted(Comparator.comparing(ClubMembership::getId)).map(m -> new ManagedMembershipResponse(m.getId(),m.getUserId(),people.get(m.getUserId()).getName(),m.getRole(),m.getStatus(),m.getRequestedAt(),m.getJoinedAt(),m.getEndedAt(),m.getVersion())).toList(),i.getArgument(2)));
    }
    private String token(long id) {
        var claims=JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject(Long.toString(id)).issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build();
        return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    private String body(Object... entries) {
        Map<String,Object> map=new LinkedHashMap<>(); for(int i=0;i<entries.length;i+=2)map.put((String)entries[i],entries[i+1]);return json.writeValueAsString(map);
    }
    private org.springframework.test.web.servlet.ResultActions action(String path,long actor,long version,String reason) throws Exception {
        return mvc.perform(post(path).header("Authorization",token(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(body("version",version,"reason",reason)));
    }
    private void setClubStatus(ClubStatus status) { field(catalog.get(10L),"status",status); }

    @Test void anonymousCannotReadOrMutateClubs() throws Exception {
        mvc.perform(get("/api/clubs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/clubs/10/membership-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(clubs,members);
    }
    @Test void creationIsPendingAndAtomicallyEstablishesFounderRoleWithoutAcceptingPrivilegeFields() throws Exception {
        mvc.perform(post("/api/clubs").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("name"," New Club ","slug","New Study_Club","description","Description","universityId",999,"status","ACTIVE","role","OWNER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.club.university.id").value(1))
                .andExpect(jsonPath("$.club.slug").value("new-study-club")).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        var created=catalog.get(30L);assertNotNull(created);assertEquals(ClubStatus.PENDING_APPROVAL,created.getStatus());
        assertTrue(people.get(4L).getRoles().contains(Role.CLUB_ADMIN));
        assertEquals(1,roster.values().stream().filter(m -> m.getClub()==created&&m.getStatus()==ACTIVE&&m.getRole()==OWNER).count());
    }
    @Test void creationRequiresCompletedProfileAndActiveUniversityAndUniqueSlug() throws Exception {
        academics.remove(4L);
        mvc.perform(post("/api/clubs").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Club","slug","club","description","Description"))).andExpect(status().isConflict());
        assertEquals(2,catalog.size());verify(clubs,never()).saveAndFlush(any());
    }
    @Test void duplicateSlugIs409() throws Exception {
        when(clubs.existsByUniversityIdAndSlug(1L,"club")).thenReturn(true);
        mvc.perform(post("/api/clubs").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Club","slug","club","description","Description"))).andExpect(status().isConflict());
        verify(clubs,never()).saveAndFlush(any());
    }
    @Test void inactiveUniversityBlocksClubCreationAndJoining() throws Exception {
        university.update("University","City",null,false,NOW);
        mvc.perform(post("/api/clubs").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Club","slug","club","description","Description"))).andExpect(status().isConflict());
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(6))).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"", "bad/slug", "-leading", "ümlaut"})
    void invalidSlugRejectedBeforeServices(String slug) throws Exception {
        mvc.perform(post("/api/clubs").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Club","slug",slug,"description","Description"))).andExpect(status().isBadRequest());
        verifyNoInteractions(clubs,members);
    }
    @Test void pendingClubIsVisibleOnlyToOwnerOrAdminAndReviewNoteIsPrivate() throws Exception {
        setClubStatus(ClubStatus.PENDING_APPROVAL);field(catalog.get(10L),"reviewNote","private review");
        mvc.perform(get("/api/clubs/10").header("Authorization",token(2))).andExpect(status().isOk()).andExpect(jsonPath("$.reviewNote").doesNotExist());
        mvc.perform(get("/api/clubs/10/review").header("Authorization",token(2))).andExpect(status().isOk()).andExpect(jsonPath("$.reviewNote").value("private review"));
        mvc.perform(get("/api/clubs/10").header("Authorization",token(5))).andExpect(status().isOk());
        mvc.perform(get("/api/clubs/10").header("Authorization",token(4))).andExpect(status().isNotFound());
        mvc.perform(get("/api/clubs/10/review").header("Authorization",token(4))).andExpect(status().isForbidden());
        mvc.perform(get("/api/clubs").header("Authorization",token(4))).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }
    @Test void adminReviewLifecycleIncludesRejectionResubmissionApprovalSuspensionAndRestore() throws Exception {
        setClubStatus(ClubStatus.PENDING_APPROVAL);
        action("/api/admin/clubs/10/reject",5,0,"Needs changes").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        action("/api/clubs/10/resubmit",2,1,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        action("/api/admin/clubs/10/approve",5,2,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        action("/api/admin/clubs/10/suspend",5,3,"Moderation").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        action("/api/admin/clubs/10/restore",5,4,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
    @Test void requiredReviewReasonAndValidTransitionAreEnforced() throws Exception {
        action("/api/admin/clubs/10/suspend",5,0,null).andExpect(status().isBadRequest());
        action("/api/admin/clubs/10/approve",5,0,null).andExpect(status().isConflict());
        action("/api/admin/clubs/10/suspend",2,0,"not admin").andExpect(status().isForbidden());
    }
    @Test void activeManagerCanEditOwnClubButGlobalClubAdminCannotEditAnotherClub() throws Exception {
        mvc.perform(patch("/api/clubs/10").header("Authorization",token(3)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Edited","version",0,"slug","hacked","universityId",999))).andExpect(status().isOk())
                .andExpect(jsonPath("$.club.slug").value("club-10")).andExpect(jsonPath("$.club.university.id").value(1));
        mvc.perform(patch("/api/clubs/10").header("Authorization",token(6)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Forbidden","version",1))).andExpect(status().isForbidden());
        assertEquals("Edited",catalog.get(10L).getName());
    }
    @ParameterizedTest @EnumSource(value=ClubStatus.class,names={"SUSPENDED","ARCHIVED"})
    void suspendedAndArchivedClubsCannotBeEditedOrAcceptApplications(ClubStatus status) throws Exception {
        setClubStatus(status);
        mvc.perform(patch("/api/clubs/10").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Edited","version",0))).andExpect(status().isConflict());
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(6))).andExpect(status().isConflict());
    }
    @Test void staleClubVersionDoesNotMutate() throws Exception {
        mvc.perform(patch("/api/clubs/10").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Stale","version",99))).andExpect(status().isConflict());assertEquals("Club 10",catalog.get(10L).getName());
    }
    @Test void studentCanApplyWithoutChoosingIdentityRoleOrStatusAndJoinMultipleClubs() throws Exception {
        mvc.perform(post("/api/clubs/20/membership-requests").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("userId",7,"role","OWNER","status","ACTIVE"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER")).andExpect(jsonPath("$.status").value("PENDING"));
        assertTrue(roster.values().stream().anyMatch(m -> m.getUserId()==4&&m.getClub().getId()==20&&m.getStatus()==PENDING));
        assertEquals(ACTIVE,roster.get(102L).getStatus());
    }
    @Test void differentUniversityAndDuplicateApplicationsAreRejected() throws Exception {
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(7))).andExpect(status().isConflict());
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(8))).andExpect(status().isConflict());
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(4))).andExpect(status().isConflict());
    }
    @Test void pendingMembershipCanBeApprovedByLocalManager() throws Exception {
        action("/api/clubs/10/memberships/103/approve",3,0,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(MEMBER,roster.get(103L).getRole());
    }
    @Test void rejectionRequiresReasonAndSupportsVersionedReapplicationAfterCooldown() throws Exception {
        action("/api/clubs/10/memberships/103/reject",3,0,null).andExpect(status().isBadRequest());
        action("/api/clubs/10/memberships/103/reject",3,0,"Not eligible now").andExpect(status().isOk());
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(8)).contentType(MediaType.APPLICATION_JSON)
                .content(body("version",1))).andExpect(status().isConflict());
        field(roster.get(103L),"updatedAt",NOW.minusSeconds(61));
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(8)).contentType(MediaType.APPLICATION_JSON)
                .content(body("version",1))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
    }
    @Test void applicantCanCancelAndMemberCanLeaveButOwnerCannotLeave() throws Exception {
        action("/api/clubs/10/membership/cancel",8,0,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LEFT"));
        action("/api/clubs/10/membership/leave",4,0,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LEFT"));
        action("/api/clubs/10/membership/leave",2,0,null).andExpect(status().isConflict());assertEquals(OWNER,roster.get(100L).getRole());
    }
    @Test void managerLeavingOneClubLosesMarkerButStudentRoleAndOtherManagersRemain() throws Exception {
        action("/api/clubs/10/membership/leave",3,0,null).andExpect(status().isOk());
        assertEquals(Set.of(Role.STUDENT),people.get(3L).getRoles());assertTrue(people.get(2L).getRoles().contains(Role.CLUB_ADMIN));
    }
    @Test void managerLeavingOneClubKeepsMarkerWhenManagingAnother() throws Exception {
        membership(201,20,3,MANAGER,ACTIVE);
        action("/api/clubs/10/membership/leave",3,0,null).andExpect(status().isOk());assertTrue(people.get(3L).getRoles().contains(Role.CLUB_ADMIN));
    }
    @Test void ownerCanPromoteMultipleManagersButManagerCannotAssignRolesOrRemoveOwner() throws Exception {
        mvc.perform(patch("/api/clubs/10/memberships/102/role").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("role","MANAGER","version",0))).andExpect(status().isOk());
        assertTrue(people.get(4L).getRoles().contains(Role.CLUB_ADMIN));assertEquals(MANAGER,roster.get(101L).getRole());
        mvc.perform(patch("/api/clubs/10/memberships/102/role").header("Authorization",token(3)).contentType(MediaType.APPLICATION_JSON)
                .content(body("role","MEMBER","version",1))).andExpect(status().isForbidden());
        action("/api/clubs/10/memberships/100/remove",3,0,"Forbidden").andExpect(status().isConflict());
        action("/api/clubs/10/memberships/101/remove",4,0,"Other manager").andExpect(status().isForbidden());
    }
    @Test void roleEndpointCannotAssignOwnerAndCannotDemoteLastOwner() throws Exception {
        mvc.perform(patch("/api/clubs/10/memberships/102/role").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("role","OWNER","version",0))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/clubs/10/memberships/100/role").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("role","MEMBER","version",0))).andExpect(status().isConflict());
    }
    @Test void ownerCanDemoteManagerAndMarkerIsRecomputed() throws Exception {
        mvc.perform(patch("/api/clubs/10/memberships/101/role").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("role","MEMBER","version",0))).andExpect(status().isOk());assertFalse(people.get(3L).getRoles().contains(Role.CLUB_ADMIN));
    }
    @Test void removedMemberCannotReapplyUntilManagerAllowsItAndCooldownPasses() throws Exception {
        action("/api/clubs/10/memberships/102/remove",3,0,"Violation").andExpect(status().isOk());
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("version",1))).andExpect(status().isConflict());
        action("/api/clubs/10/memberships/102/allow-reapplication",3,1,null).andExpect(status().isOk());field(roster.get(102L),"updatedAt",NOW.minusSeconds(61));
        mvc.perform(post("/api/clubs/10/membership-requests").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("version",2))).andExpect(status().isOk());
    }
    @Test void onlyAdminCanSuspendAndRestoreMembershipAndRestorationDoesNotRestoreManagerRole() throws Exception {
        action("/api/admin/clubs/10/memberships/101/suspend",2,0,"Reason").andExpect(status().isForbidden());
        action("/api/admin/clubs/10/memberships/101/suspend",5,0,"Reason").andExpect(status().isOk());
        assertEquals(MEMBER,roster.get(101L).getRole());assertFalse(people.get(3L).getRoles().contains(Role.CLUB_ADMIN));
        action("/api/admin/clubs/10/memberships/101/restore",5,1,null).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER"));
        action("/api/admin/clubs/10/memberships/100/suspend",5,0,"Owner").andExpect(status().isConflict());
    }
    @Test void ownershipTransferLocksUsersInOrderAndDowngradesOldOwnerBeforePromotingTarget() throws Exception {
        mvc.perform(post("/api/clubs/10/ownership-transfer").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("targetMembershipId",102,"clubVersion",0,"ownerMembershipVersion",0,"targetMembershipVersion",0))).andExpect(status().isOk());
        assertEquals(MANAGER,roster.get(100L).getRole());assertEquals(OWNER,roster.get(102L).getRole());
        assertEquals(1,roster.values().stream().filter(m -> m.getClub().getId()==10&&m.getRole()==OWNER&&m.getStatus()==ACTIVE).count());
        var locks=inOrder(users,clubs);locks.verify(users).findByIdForUpdate(2L);locks.verify(users).findByIdForUpdate(4L);locks.verify(clubs).findByIdForUpdate(10L);
        var saves=inOrder(members);saves.verify(members).saveAndFlush(roster.get(100L));saves.verify(members).saveAndFlush(roster.get(102L));
        verify(em).refresh(people.get(2L),LockModeType.PESSIMISTIC_WRITE);
        action("/api/clubs/10/membership/leave",2,1,null).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(longs={100,103,200})
    void transferRejectsSameOwnerPendingMemberOrDifferentClub(long target) throws Exception {
        mvc.perform(post("/api/clubs/10/ownership-transfer").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("targetMembershipId",target,"clubVersion",0,"ownerMembershipVersion",0,"targetMembershipVersion",0)))
                .andExpect(target==200?status().isNotFound():status().isConflict());
        assertEquals(OWNER,roster.get(100L).getRole());
    }
    @Test void staleOwnershipVersionCannotPartiallyTransfer() throws Exception {
        mvc.perform(post("/api/clubs/10/ownership-transfer").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("targetMembershipId",102,"clubVersion",0,"ownerMembershipVersion",99,"targetMembershipVersion",0))).andExpect(status().isConflict());
        verify(members,never()).saveAndFlush(any());
    }
    @Test void archiveRemovesOnlyUnneededGlobalMarkersAndPreservesHistoricalMemberships() throws Exception {
        membership(201,20,3,MANAGER,ACTIVE);
        action("/api/clubs/10/archive",3,0,null).andExpect(status().isForbidden());
        action("/api/clubs/10/archive",2,0,null).andExpect(status().isOk());
        assertEquals(ClubStatus.ARCHIVED,catalog.get(10L).getStatus());assertFalse(people.get(2L).getRoles().contains(Role.CLUB_ADMIN));
        assertTrue(people.get(3L).getRoles().contains(Role.CLUB_ADMIN));assertEquals(6,roster.size());
        mvc.perform(get("/api/clubs/10").header("Authorization",token(4))).andExpect(status().isOk());
        mvc.perform(get("/api/clubs/10").header("Authorization",token(8))).andExpect(status().isNotFound());
        action("/api/clubs/10/membership/leave",2,0,null).andExpect(status().isOk());
    }
    @Test void changedManagerSetDuringArchiveReturnsConflict() throws Exception {
        when(members.activeManagerUserIds(10L)).thenReturn(List.of(2L,3L),List.of(2L,3L,4L));
        action("/api/clubs/10/archive",2,0,null).andExpect(status().isConflict());assertEquals(ClubStatus.ACTIVE,catalog.get(10L).getStatus());
    }
    @Test void managementRosterAndDecisionEndpointsRejectCrossClubAndMembershipIdMixups() throws Exception {
        mvc.perform(get("/api/clubs/10/memberships").header("Authorization",token(6))).andExpect(status().isForbidden());
        action("/api/clubs/10/memberships/103/approve",6,0,null).andExpect(status().isForbidden());
        action("/api/clubs/10/memberships/200/approve",2,0,null).andExpect(status().isNotFound());
        mvc.perform(get("/api/clubs/10/memberships").header("Authorization",token(3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").doesNotExist()).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].bio").doesNotExist());
    }
    @Test void ownMembershipListContainsOnlyCallerAndNoAccountSecrets() throws Exception {
        mvc.perform(get("/api/me/club-memberships").header("Authorization",token(4))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].id").value(102))
                .andExpect(jsonPath("$.content[0].user").doesNotExist()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void adminQueueIsPrivateAndIncludesReviewDetailsOnlyForAdmin() throws Exception {
        mvc.perform(get("/api/admin/clubs").header("Authorization",token(2))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/clubs").header("Authorization",token(5))).andExpect(status().isOk());
    }
    @Test void revokedMarkerOrSuspendedMembershipImmediatelyDeniesManagement() throws Exception {
        people.get(3L).revokeRole(Role.CLUB_ADMIN);
        action("/api/clubs/10/memberships/103/approve",3,0,null).andExpect(status().isForbidden());
        people.get(3L).grantRole(Role.CLUB_ADMIN);field(roster.get(101L),"status",SUSPENDED);field(roster.get(101L),"role",MEMBER);
        action("/api/clubs/10/memberships/103/approve",3,0,null).andExpect(status().isForbidden());
    }
    @Test void profileUniversityChangeIsBlockedForPendingActiveOrSuspendedMembership() {
        when(members.existsByUserIdAndStatusIn(eq(4L),anyCollection())).thenReturn(true);
        assertThrows(ResourceConflictException.class,() -> context.getBean(StudentProfilePolicy.class).requireUniversityChangeAllowed(4L));
    }
    @ParameterizedTest @ValueSource(strings={"size=0","size=101","page=-1"})
    void invalidPaginationIs400(String parameter) throws Exception {
        var pair=parameter.split("=");mvc.perform(get("/api/clubs").param(pair[0],pair[1]).header("Authorization",token(4))).andExpect(status().isBadRequest());
    }
    @Test void unsafeLogoAndMissingVersionAreRejected() throws Exception {
        mvc.perform(patch("/api/clubs/10").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("logoUrl","javascript:alert(1)","version",0))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/clubs/10").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Changed"))).andExpect(status().isBadRequest());
        verify(clubs,never()).saveAndFlush(any());
    }
    @Test void directServiceCallsCannotBypassOwnershipAndRoleChecks() {
        var holder=SecurityContextHolder.getContext();
        try {
            holder.setAuthentication(new UsernamePasswordAuthenticationToken("6","unused",List.of(new SimpleGrantedAuthority("ROLE_CLUB_ADMIN"))));
            assertThrows(AccessDeniedException.class,() -> context.getBean(ClubService.class).update(10L,new UpdateClubRequest("Bad",null,null,0L)));
            assertThrows(AccessDeniedException.class,() -> context.getBean(ClubMembershipService.class).role(10L,102L,new ChangeMembershipRoleRequest(MANAGER,0L)));
            assertThrows(AccessDeniedException.class,() -> context.getBean(ClubService.class).review(10L,new ClubActionRequest(0L,null),ClubService.ReviewAction.APPROVE));
            verify(clubs,never()).saveAndFlush(any());verify(members,never()).saveAndFlush(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void roleRevokedWhileWaitingForLockCannotBeUsedByTheSameRequest() throws Exception {
        doAnswer(i -> { people.get(3L).revokeRole(Role.CLUB_ADMIN); return null; }).when(em)
                .refresh(people.get(3L),LockModeType.PESSIMISTIC_WRITE);
        action("/api/clubs/10/memberships/103/approve",3,0,null).andExpect(status().isForbidden());
        assertEquals(PENDING,roster.get(103L).getStatus());verify(members,never()).saveAndFlush(any());
    }
    @Test void membershipRevokedAfterPreflightIsRecheckedUnderClubLock() throws Exception {
        doAnswer(i -> { field(roster.get(101L),"status",SUSPENDED);field(roster.get(101L),"role",MEMBER);return null; })
                .when(em).refresh(catalog.get(10L),LockModeType.PESSIMISTIC_WRITE);
        action("/api/clubs/10/memberships/103/approve",3,0,null).andExpect(status().isForbidden());
        assertEquals(PENDING,roster.get(103L).getStatus());verify(members,never()).saveAndFlush(any());
    }
    @Test void ownerChangedWhileAcquiringLocksCausesConflictBeforeAnyWrite() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(members.activeOwner(10L)).thenAnswer(i -> {
            if(calls.getAndIncrement()==0) return Optional.of(roster.get(100L));
            field(roster.get(100L),"role",MANAGER);field(roster.get(101L),"role",OWNER);
            return Optional.of(roster.get(101L));
        });
        mvc.perform(post("/api/clubs/10/ownership-transfer").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("targetMembershipId",102,"clubVersion",0,"ownerMembershipVersion",0,"targetMembershipVersion",0)))
                .andExpect(status().isConflict());verify(members,never()).saveAndFlush(any());
    }
    @Test void approvingAnOwnerlessClubIsRejected() throws Exception {
        setClubStatus(ClubStatus.PENDING_APPROVAL);roster.remove(100L);
        action("/api/admin/clubs/10/approve",5,0,null).andExpect(status().isConflict());
        verify(clubs,never()).saveAndFlush(any());
    }

    @Test void inactiveUniversityDoesNotPreventDemotingAnExistingManager() throws Exception {
        university.update("University","City",null,false,NOW);
        mvc.perform(patch("/api/clubs/10/memberships/101/role").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON)
                .content(body("role","MEMBER","version",0))).andExpect(status().isOk());
        assertFalse(people.get(3L).getRoles().contains(Role.CLUB_ADMIN));
    }

    @Test void lockAndUniqueConflictsReturn409WithoutSqlDetails() throws Exception {
        doThrow(new CannotAcquireLockException("private SQL")).when(clubs).findByIdForUpdate(10L);
        action("/api/clubs/10/membership/leave",4,0,null).andExpect(status().isConflict());
        doThrow(new DataIntegrityViolationException("private SQL")).when(clubs).saveAndFlush(any());
        mvc.perform(post("/api/clubs").header("Authorization",token(4)).contentType(MediaType.APPLICATION_JSON)
                .content(body("name","Club","slug","new-club","description","Description"))).andExpect(status().isConflict());
    }
}
