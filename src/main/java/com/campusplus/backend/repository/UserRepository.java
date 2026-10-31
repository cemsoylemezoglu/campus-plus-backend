package com.campusplus.backend.repository;

import com.campusplus.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User,Long> {

    // Serialize business writers without blocking FK KEY SHARE checks for notification recipients.
    @Query(value="select u.* from public.users u where u.id = :id for no key update",nativeQuery=true)
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

}
