package com.campusplus.backend.repository;

import com.campusplus.backend.entity.UserBlock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    Optional<UserBlock> findByBlockerIdAndBlockedId(long blockerId, long blockedId);
    boolean existsByBlockerIdAndBlockedIdAndActiveTrue(long blockerId, long blockedId);
}
