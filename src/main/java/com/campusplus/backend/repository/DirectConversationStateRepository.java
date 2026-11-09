package com.campusplus.backend.repository;

import com.campusplus.backend.entity.DirectConversationState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectConversationStateRepository extends JpaRepository<DirectConversationState, Long> {
    Optional<DirectConversationState> findByConversationIdAndUserId(long conversationId, long userId);
}
