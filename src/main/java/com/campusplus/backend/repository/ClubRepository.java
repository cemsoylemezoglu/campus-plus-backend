package com.campusplus.backend.repository;

import com.campusplus.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClubRepository extends JpaRepository<Club, Long> {
    boolean existsByUniversityIdAndSlug(Long universityId, String slug);
    @Override @EntityGraph(attributePaths="university") Optional<Club> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Club c where c.id=:id")
    Optional<Club> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_READ) @Query("select c from Club c where c.id=:id")
    Optional<Club> findByIdForShare(@Param("id") Long id);
    @EntityGraph(attributePaths="university")
    @Query("select c from Club c where (:universityId is null or c.university.id=:universityId) "
            + "and (:status is null or c.status=:status) and (lower(c.name) like :pattern escape '!' or c.slug like :pattern escape '!')")
    Page<Club> search(@Param("universityId") Long universityId, @Param("status") ClubStatus status,
                      @Param("pattern") String pattern, Pageable pageable);
}
