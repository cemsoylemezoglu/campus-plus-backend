package com.campusplus.backend.repository;
import com.campusplus.backend.dto.comment.CommentModerationResponse;
import com.campusplus.backend.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
public interface CommentModerationRepository extends Repository<CommentModeration,Long> {
    CommentModeration save(CommentModeration action);
    void flush();
    @Query("select new com.campusplus.backend.dto.comment.CommentModerationResponse(m.id,m.actor.id,m.scope,m.action,m.reason,m.createdAt,m.resultingVersion) "
        +"from CommentModeration m where m.comment.id=:id and (:scope is null or m.scope=:scope)")
    Page<CommentModerationResponse> history(@Param("id") Long id,@Param("scope") CommentModerationScope scope,Pageable page);
}
