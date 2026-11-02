package com.campusplus.backend.repository;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostComment,Long> {
    String FROM=" from PostComment x join x.post p join p.university u left join p.club c join x.author a left join x.root r ";
    String FIELDS="x.status,x.resourceHidden,x.platformHidden,x.createdAt,x.updatedAt,x.removedAt,x.version)";
    String ROW="select new com.campusplus.backend.dto.comment.CommentRow(x.id,p.id,r.id,a.id,a.name,x.body,"+FIELDS+FROM;
    String SAFE_ROW="select new com.campusplus.backend.dto.comment.CommentRow(x.id,p.id,r.id,"
        +"case when x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE then a.id else null end,"
        +"case when x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE then a.name else null end,"
        +"case when x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE then x.body else null end,"+FIELDS+FROM;
    String CONTENT="x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE and x.resourceHidden=false and x.platformHidden=false "
        +"and (r.id is null or (r.resourceHidden=false and r.platformHidden=false)) ";
    String ROOT="x.root is null and x.resourceHidden=false and x.platformHidden=false and ("
        +"x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE or exists(select child.id from PostComment child "
        +"where child.root=x and child.status=com.campusplus.backend.entity.CommentStatus.ACTIVE and child.resourceHidden=false and child.platformHidden=false)) ";
    @Override @EntityGraph(attributePaths={"post","post.university","post.club","root"}) Optional<PostComment> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select x from PostComment x where x.id=:id and x.post.id=:postId")
    Optional<PostComment> lock(@Param("id") Long id,@Param("postId") Long postId);
    @Query(SAFE_ROW+"where p.id=:postId and "+PostRepository.VISIBLE+"and "+ROOT+"order by x.createdAt,x.id")
    List<CommentRow> rootsFirst(@Param("postId") Long postId,@Param("userId") Long userId,@Param("universityId") Long universityId,Pageable page);
    @Query(SAFE_ROW+"where p.id=:postId and "+PostRepository.VISIBLE+"and "+ROOT
        +"and (x.createdAt>:time or (x.createdAt=:time and x.id>:afterId)) order by x.createdAt,x.id")
    List<CommentRow> rootsAfter(@Param("postId") Long postId,@Param("userId") Long userId,@Param("universityId") Long universityId,
        @Param("time") Instant time,@Param("afterId") Long afterId,Pageable page);
    @Query(SAFE_ROW+"where p.id=:postId and r.id=:rootId and "+PostRepository.VISIBLE+"and "+CONTENT+"order by x.createdAt,x.id")
    List<CommentRow> repliesFirst(@Param("postId") Long postId,@Param("rootId") Long rootId,@Param("userId") Long userId,
        @Param("universityId") Long universityId,Pageable page);
    @Query(SAFE_ROW+"where p.id=:postId and r.id=:rootId and "+PostRepository.VISIBLE+"and "+CONTENT
        +"and (x.createdAt>:time or (x.createdAt=:time and x.id>:afterId)) order by x.createdAt,x.id")
    List<CommentRow> repliesAfter(@Param("postId") Long postId,@Param("rootId") Long rootId,@Param("userId") Long userId,
        @Param("universityId") Long universityId,@Param("time") Instant time,@Param("afterId") Long afterId,Pageable page);
    @Query(SAFE_ROW+"where p.id=:postId and x.id=:id and "+PostRepository.VISIBLE+"and (("+CONTENT+") or ("+ROOT+"))")
    Optional<CommentRow> visible(@Param("postId") Long postId,@Param("id") Long id,@Param("userId") Long userId,@Param("universityId") Long universityId);
    @Query(ROW+"where a.id=:userId") Page<CommentRow> own(@Param("userId") Long userId,Pageable page);
    @Query(ROW+"where (:postId is null or p.id=:postId) and (:status is null or x.status=:status)")
    Page<CommentRow> managed(@Param("postId") Long postId,@Param("status") CommentStatus status,Pageable page);
    @Query(ROW+"where x.id=:id") Optional<CommentRow> managedDetail(@Param("id") Long id);
    @Query("select count(x)"+FROM+"where p.id=:postId and "+CONTENT)
    long visibleCount(@Param("postId") Long postId);
    @Query("select new com.campusplus.backend.dto.comment.SourceCount(x.root.id,count(x)) from PostComment x "
        +"where x.root.id in :ids and x.status=com.campusplus.backend.entity.CommentStatus.ACTIVE "
        +"and x.resourceHidden=false and x.platformHidden=false group by x.root.id")
    List<SourceCount> replyCounts(@Param("ids") Collection<Long> ids);
}
