package com.campusplus.backend.repository;
import com.campusplus.backend.entity.ClubContentLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ClubContentLikeRepository extends JpaRepository<ClubContentLike,Long> {
    String FROM=" from ClubContentLike x left join x.event e left join x.announcement n ";
    String SOURCE="(e.id=:eventId or n.id=:announcementId) ";
    @Query("select x"+FROM+"where "+SOURCE+"and x.user.id=:userId")
    Optional<ClubContentLike> own(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId,@Param("userId") Long userId);
    @Query("select count(x)"+FROM+"where "+SOURCE+"and x.active=true")
    long activeCount(@Param("eventId") Long eventId,@Param("announcementId") Long announcementId);
}
