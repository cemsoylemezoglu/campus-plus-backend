package com.campusplus.backend.repository;

import com.campusplus.backend.entity.University;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UniversityRepository extends JpaRepository<University, Long> {
    boolean existsByCode(String code);

    @Query("select u from University u where (:includeInactive = true or u.active = true) "
            + "and (lower(u.name) like :pattern escape '!' or lower(u.code) like :pattern escape '!')")
    Page<University> search(@Param("pattern") String pattern,
                            @Param("includeInactive") boolean includeInactive, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from University u where u.id = :id")
    Optional<University> findByIdForShare(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from University u where u.id = :id")
    Optional<University> findByIdForUpdate(@Param("id") Long id);
}
