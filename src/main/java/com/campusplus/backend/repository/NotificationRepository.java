package com.campusplus.backend.repository;
import com.campusplus.backend.entity.Notification;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface NotificationRepository extends JpaRepository<Notification,Long> {
    @EntityGraph(attributePaths={"club","club.university","announcement","event","participation","membership"})
    @Query("select n from Notification n where n.recipientId=:recipient and (:unread=false or n.readAt is null)")
    Page<Notification> inbox(@Param("recipient") Long recipient,@Param("unread") boolean unread,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByIdAndRecipientId(Long id,Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
    @Modifying(flushAutomatically=true)
    @Query("update Notification n set n.readAt=case when n.createdAt>:now then n.createdAt else :now end, "
        +"n.version=n.version+1 where n.recipientId=:recipient and n.id<=:upToId and n.readAt is null")
    int readAll(@Param("recipient") Long recipient,@Param("upToId") Long upToId,@Param("now") Instant now);
}
