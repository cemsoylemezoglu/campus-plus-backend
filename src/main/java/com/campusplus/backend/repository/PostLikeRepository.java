package com.campusplus.backend.repository;
import com.campusplus.backend.entity.PostLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PostLikeRepository extends JpaRepository<PostLike,Long> {
    Optional<PostLike> findByPostIdAndUserId(Long postId,Long userId);
    long countByPostIdAndActiveTrue(Long postId);
    boolean existsByPostIdAndUserIdAndActiveTrue(Long postId,Long userId);
}
