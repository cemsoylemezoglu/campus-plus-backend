package com.campusplus.backend.repository;

import com.campusplus.backend.entity.DirectConversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DirectConversationRepository extends JpaRepository<DirectConversation, Long> {
    @Query("select c from DirectConversation c where c.lowerUser.id=:lower and c.higherUser.id=:higher")
    Optional<DirectConversation> findPair(@Param("lower") long lower, @Param("higher") long higher);
}
