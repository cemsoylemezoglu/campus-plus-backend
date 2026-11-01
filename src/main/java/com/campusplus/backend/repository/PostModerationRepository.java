package com.campusplus.backend.repository;

import com.campusplus.backend.dto.post.PostModerationResponse;
import com.campusplus.backend.entity.PostModeration;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** No delete/update API. The runtime DB role also lacks those audit privileges. */
public interface PostModerationRepository extends Repository<PostModeration, Long> {
    PostModeration save(PostModeration action);
    void flush();

    @Query("select new com.campusplus.backend.dto.post.PostModerationResponse(m.id,m.actor.id,m.action,m.reason,m.createdAt,m.resultingVersion) "
            + "from PostModeration m where m.post.id=:postId")
    Page<PostModerationResponse> history(@Param("postId") Long postId, Pageable page);
}
