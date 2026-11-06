package com.campusplus.backend.repository;
import com.campusplus.backend.dto.comment.SourceCount;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClubCommentRepository extends JpaRepository<ClubComment,Long> {
    String FROM=" from ClubComment x left join x.event e left join e.club ec left join ec.university eu "
        +"left join x.announcement n left join n.club nc left join nc.university nu join x.author a left join x.root r ";
    String SOURCE="(e.id=:eventId or n.id=:announcementId) ";
    String FIELDS="x.status,x.resourceHidden,x.platformHidden,x.createdAt,x.updatedAt,x.removedAt,x.version)";
    String ROW="select new com.campusplus.backend.dto.clubinteraction.ClubCommentRow(x.id,e.id,n.id,r.id,a.id,a.name,x.body,"+FIELDS+FROM;
    String SAFE_ROW="select new com.campusplus.backend.dto.clubinteraction.ClubCommentRow(x.id,e.id,n.id,r.id,"
        +"case when x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE then a.id else null end,"
        +"case when x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE then a.name else null end,"
        +"case when x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE then x.body else null end,"+FIELDS+FROM;
    String CONTENT="x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE and x.resourceHidden=false and x.platformHidden=false "
        +"and (r.id is null or (r.resourceHidden=false and r.platformHidden=false)) ";
    String ROOT="x.root is null and x.resourceHidden=false and x.platformHidden=false and ("
        +"x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE or exists(select child.id from ClubComment child "
        +"where child.root=x and child.status=com.campusplus.backend.entity.CommentStatus.ACTIVE and child.resourceHidden=false and child.platformHidden=false)) ";
    @Override @EntityGraph(attributePaths={"event","event.club","event.club.university","announcement","announcement.club","announcement.club.university","root"}) Optional<ClubComment> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select x from ClubComment x where x.id=:id and (x.event.id=:eventId or x.announcement.id=:announcementId)")
    Optional<ClubComment> lock(@Param("id") Long id,@Param("eventId") Long eventId,@Param("announcementId") Long announcementId);
    @Query(SAFE_ROW+"where "+SOURCE+"and "+ClubInteractionPredicates.VISIBLE+"and "+ROOT+"order by x.createdAt,x.id")
    List<ClubCommentRow> rootsFirst(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("userId") Long userId,Pageable page);
    @Query(SAFE_ROW+"where "+SOURCE+"and "+ClubInteractionPredicates.VISIBLE+"and "+ROOT
        +"and (x.createdAt>:time or (x.createdAt=:time and x.id>:afterId)) order by x.createdAt,x.id")
    List<ClubCommentRow> rootsAfter(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("userId") Long userId,
        @Param("time") Instant time,@Param("afterId") Long afterId,Pageable page);
    @Query(SAFE_ROW+"where "+SOURCE+"and r.id=:rootId and "+ClubInteractionPredicates.VISIBLE+"and "+CONTENT+"order by x.createdAt,x.id")
    List<ClubCommentRow> repliesFirst(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("rootId") Long rootId,@Param("userId") Long userId,
        Pageable page);
    @Query(SAFE_ROW+"where "+SOURCE+"and r.id=:rootId and "+ClubInteractionPredicates.VISIBLE+"and "+CONTENT
        +"and (x.createdAt>:time or (x.createdAt=:time and x.id>:afterId)) order by x.createdAt,x.id")
    List<ClubCommentRow> repliesAfter(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("rootId") Long rootId,@Param("userId") Long userId,
        @Param("time") Instant time,@Param("afterId") Long afterId,Pageable page);
    @Query(SAFE_ROW+"where "+SOURCE+"and x.id=:id and "+ClubInteractionPredicates.VISIBLE+"and (("+CONTENT+") or ("+ROOT+"))")
    Optional<ClubCommentRow> visible(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("id") Long id,@Param("userId") Long userId);
    @Query(ROW+"where a.id=:userId") Page<ClubCommentRow> own(@Param("userId") Long userId,Pageable page);
    @Query(ROW+"where (:status is null or x.status=:status)")
    Page<ClubCommentRow> managed(@Param("status") CommentStatus status,Pageable page);
    @Query(ROW+"where x.id=:id") Optional<ClubCommentRow> managedDetail(@Param("id") Long id);
    @Query("select count(x)"+FROM+"where "+SOURCE+"and "+ClubInteractionPredicates.VISIBLE+"and "+CONTENT)
    long visibleCount(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("userId") Long userId);
    @Query("select new com.campusplus.backend.dto.comment.SourceCount(x.root.id,count(x)) from ClubComment x "
        +"where x.root.id in :ids and x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE "
        +"and x.resourceHidden=false and x.platformHidden=false group by x.root.id")
    List<SourceCount> replyCounts(@Param("ids") Collection<Long> ids);
}
