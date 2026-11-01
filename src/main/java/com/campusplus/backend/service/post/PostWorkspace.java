package com.campusplus.backend.service.post;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.PostRepository;
import com.campusplus.backend.security.PostPolicy;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.persistence.*;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PostWorkspace {
    public final ClubWorkspace core;
    public final PostRepository posts;
    public final PostPolicy policy;
    private final EntityManager em;
    public PostWorkspace(ClubWorkspace core, PostRepository posts, PostPolicy policy, EntityManager em) {
        this.core = core; this.posts = posts; this.policy = policy; this.em = em;
    }
    public record Locked(User actor, Post post, Club club, University university) {}
    public Post post(Long id) { return posts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı.")); }
    public void preflightClub(Long clubId) {
        Club club = core.club(clubId); User actor = core.actor();
        policy.requireManager(club, actor, core.local(club, actor.getId()));
    }
    public Post own(Long id) {
        Post post = post(id); policy.requireOwn(post, core.actor()); return post;
    }
    public Post clubPost(Long clubId, Long id) {
        Post post = post(id);
        if (post.getClub() == null || !post.getClub().getId().equals(clubId)) policy.missing();
        return post;
    }
    public University university(Long id) {
        University u = core.universities.findByIdForShare(id).orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        core.refreshUniversity(u); return u;
    }
    public void active(University u, Club club) {
        if (!u.isActive()) throw new ResourceConflictException("Üniversite aktif değil.");
        if (club != null) core.policy.requireActive(club);
    }
    public Locked createStudent() {
        User actor = core.lockUser(core.current.userId()); policy.requireStudent(actor);
        University u = university(core.profile(actor.getId()).getUniversity().getId()); active(u, null);
        return new Locked(actor, null, null, u);
    }
    public Locked createClub(Long clubId) {
        preflightClub(clubId);
        User actor = core.lockUser(core.current.userId()); Club club = lockClub(clubId);
        policy.requireManager(club, actor, core.local(club, actor.getId()));
        University u = university(club.getUniversity().getId()); active(u, club);
        return new Locked(actor, null, club, u);
    }
    private Club lockClub(Long id) {
        Club club = core.clubs.findByIdForShare(id).orElseThrow(() -> new ResourceNotFoundException("Kulüp bulunamadı."));
        em.refresh(club, LockModeType.PESSIMISTIC_READ); return club;
    }
    /** Own student or local club route; never trusts a body-supplied author, university or club. */
    public Locked write(Long clubId, Long id, boolean removing) {
        Post preview;
        if (clubId == null) preview = own(id);
        else { preflightClub(clubId); preview = clubPost(clubId, id); }
        User actor = core.lockUser(core.current.userId());
        Club club = clubId == null ? null : lockClub(clubId);
        if (club != null) policy.requireManager(club, actor, core.local(club, actor.getId()));
        University u = university(preview.getUniversity().getId());
        Post post = lockPost(id);
        if (!Objects.equals(post.getUniversity().getId(), u.getId())
                || !Objects.equals(post.getClub() == null ? null : post.getClub().getId(), clubId)) policy.missing();
        if (club == null) {
            policy.requireOwn(post, actor);
            if (!removing) {
                policy.requireStudent(actor); policy.requireSameUniversity(post, core.profile(actor.getId()));
            }
        }
        if (!removing) active(u, club);
        else if (club != null && club.getStatus() != ClubStatus.ACTIVE && !core.policy.admin(actor)) core.policy.deny();
        return new Locked(actor, post, club, u);
    }
    public Locked moderate(Long id) {
        core.policy.requireAdmin(core.actor()); Post preview = post(id);
        User actor = core.lockUser(core.current.userId()); core.policy.requireAdmin(actor);
        Club club = preview.getClub() == null ? null : lockClub(preview.getClub().getId());
        University u = university(preview.getUniversity().getId());
        return new Locked(actor, lockPost(id), club, u);
    }
    private Post lockPost(Long id) {
        Post post = posts.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı."));
        em.refresh(post, LockModeType.PESSIMISTIC_WRITE); return post;
    }
}
