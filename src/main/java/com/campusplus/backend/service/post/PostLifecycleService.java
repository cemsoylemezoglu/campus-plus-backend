package com.campusplus.backend.service.post;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.PostRepository;
import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Internal coordinator. Caller holds users -> club WRITE -> events -> announcements. */
@Service @Transactional(propagation = Propagation.MANDATORY)
public class PostLifecycleService {
    private final PostRepository posts;
    private final EntityManager em;
    public PostLifecycleService(PostRepository posts, EntityManager em) { this.posts = posts; this.em = em; }
    public void clubArchived(Club club, User actor, Instant now) {
        for (Post post : posts.lockUnremoved(club.getId())) {
            em.refresh(post, LockModeType.PESSIMISTIC_WRITE);
            post.remove(actor, "Kulüp arşivlendi.", now); posts.saveAndFlush(post);
        }
    }
}
