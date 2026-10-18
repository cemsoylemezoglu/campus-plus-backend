package com.campusplus.backend.repository;

import com.campusplus.backend.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    @Override
    @EntityGraph(attributePaths = "university")
    Optional<StudentProfile> findById(Long userId);
}
