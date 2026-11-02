package com.campusplus.backend.repository;
import com.campusplus.backend.dto.comment.SourceCount;
import com.campusplus.backend.entity.PostCommentLike;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike,Long> {
    Optional<PostCommentLike> findByCommentIdAndUserId(Long commentId,Long userId);
    @Query("select new com.campusplus.backend.dto.comment.SourceCount(l.comment.id,count(l)) from PostCommentLike l "
        +"where l.comment.id in :ids and l.active=true group by l.comment.id")
    List<SourceCount> counts(@Param("ids") Collection<Long> ids);
    @Query("select l.comment.id from PostCommentLike l where l.comment.id in :ids and l.user.id=:userId and l.active=true")
    List<Long> liked(@Param("ids") Collection<Long> ids,@Param("userId") Long userId);
}
