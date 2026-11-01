package com.campusplus.backend.repository;

import com.campusplus.backend.dto.post.PostRow;
import com.campusplus.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    String ROW = "select new com.campusplus.backend.dto.post.PostRow(p.id,u.id,u.name,a.id,a.name,c.id,c.name,"
            + "p.publisher,p.audience,p.status,p.body,p.publishedAt,p.updatedAt,p.createdAt,p.removedAt,"
            + "r.id,p.removalReason,p.moderationHidden,p.version) from Post p join p.university u "
            + "join p.createdBy a left join p.club c left join p.removedBy r ";
    String VISIBLE = "p.status=com.campusplus.backend.entity.PostStatus.PUBLISHED and p.moderationHidden=false "
            + "and u.active=true and u.id=:universityId and (c.id is null or c.status=com.campusplus.backend.entity.ClubStatus.ACTIVE) "
            + "and (p.audience=com.campusplus.backend.entity.PostAudience.UNIVERSITY_STUDENTS or "
            + "exists(select m.id from ClubMembership m where m.club=p.club and m.userId=:userId "
            + "and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE)) ";

    @Override @EntityGraph(attributePaths = {"university", "club", "createdBy", "removedBy"})
    Optional<Post> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id=:id")
    Optional<Post> findForUpdate(@Param("id") Long id);

    // Separate first-page query: a nullable timestamptz IS NULL parameter is untyped in pgJDBC/PG.
    @Query(ROW + "where " + VISIBLE + "and (:clubId is null or c.id=:clubId) order by p.publishedAt desc,p.id desc")
    List<PostRow> feedFirst(@Param("userId") Long userId, @Param("universityId") Long universityId,
            @Param("clubId") Long clubId, Pageable page);

    @Query(ROW + "where " + VISIBLE + "and (:clubId is null or c.id=:clubId) "
            + "and (p.publishedAt<:beforeTime or (p.publishedAt=:beforeTime and p.id<:beforeId)) "
            + "order by p.publishedAt desc,p.id desc")
    List<PostRow> feed(@Param("userId") Long userId, @Param("universityId") Long universityId,
            @Param("clubId") Long clubId, @Param("beforeTime") Instant beforeTime, @Param("beforeId") Long beforeId, Pageable page);

    @Query(ROW + "where p.id=:id and " + VISIBLE)
    Optional<PostRow> visible(@Param("id") Long id, @Param("userId") Long userId, @Param("universityId") Long universityId);

    @Query(ROW + "where p.publisher=com.campusplus.backend.entity.PostPublisher.STUDENT and a.id=:userId "
            + "and (:status is null or p.status=:status)")
    Page<PostRow> own(@Param("userId") Long userId, @Param("status") PostStatus status, Pageable page);

    @Query(ROW + "where c.id=:clubId and (:status is null or p.status=:status)")
    Page<PostRow> managed(@Param("clubId") Long clubId, @Param("status") PostStatus status, Pageable page);

    @Query(ROW + "where (:status is null or p.status=:status) and (:hidden is null or p.moderationHidden=:hidden) "
            + "and (:universityId is null or u.id=:universityId) and (:clubId is null or c.id=:clubId)")
    Page<PostRow> moderation(@Param("status") PostStatus status, @Param("hidden") Boolean hidden,
            @Param("universityId") Long universityId, @Param("clubId") Long clubId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.club.id=:clubId and p.status<>com.campusplus.backend.entity.PostStatus.REMOVED order by p.id")
    List<Post> lockUnremoved(@Param("clubId") Long clubId);
}
