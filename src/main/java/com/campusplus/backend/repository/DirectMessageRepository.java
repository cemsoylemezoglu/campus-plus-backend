package com.campusplus.backend.repository;

import com.campusplus.backend.entity.DirectMessage;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    // Explicit field paths: getConversationId/getSenderId are convenience getters, not JPA attributes.
    @Query("select m from DirectMessage m where m.conversation.id=:conversationId and m.sender.id=:senderId and m.clientMessageId=:key")
    Optional<DirectMessage> findByConversationIdAndSenderIdAndClientMessageId(@Param("conversationId") long conversationId,
            @Param("senderId") long senderId, @Param("key") UUID key);
    @Query("select (count(m)>0) from DirectMessage m where m.conversation.id=:conversationId and m.messageNumber=:messageNumber")
    boolean existsByConversationIdAndMessageNumber(@Param("conversationId") long conversationId, @Param("messageNumber") long messageNumber);
}
