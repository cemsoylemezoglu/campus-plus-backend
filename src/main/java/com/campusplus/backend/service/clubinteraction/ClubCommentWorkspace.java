package com.campusplus.backend.service.clubinteraction;

import com.campusplus.backend.dto.clubinteraction.ClubSourceRef;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.ClubCommentRepository;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;

/** Used inside service transactions, after the source lock and before like/audit writes. */
@Component
public class ClubCommentWorkspace {
    public final ClubWorkspace core;
    public final ClubInteractionWorkspace sources;
    public final ClubCommentRepository comments;
    private final EntityManager em;

    public ClubCommentWorkspace(ClubWorkspace core, ClubInteractionWorkspace sources, ClubCommentRepository comments, EntityManager em) {
        this.core = core; this.sources = sources; this.comments = comments; this.em = em;
    }
    public ClubComment comment(Long id) { return comments.findById(id).orElseThrow(ClubCommentWorkspace::missing); }
    public void source(ClubComment comment, ClubSourceRef ref) { if (!comment.source().equals(ref)) throw missing(); }
    public void own(ClubComment comment, User actor) { if (!comment.getAuthor().getId().equals(actor.getId())) throw missing(); }
    public void visible(ClubComment comment) { if (!comment.visibleContent()) throw missing(); }
    public void root(ClubComment comment) {
        visible(comment);
        if (comment.getRoot() != null) throw new ResourceConflictException("Yanıt yalnız kök yoruma eklenebilir.");
    }
    public ClubComment lock(Long id, ClubSourceRef ref) {
        var preview = comment(id); source(preview, ref);
        if (preview.getRoot() != null) locked(preview.getRoot().getId(), ref);
        return locked(id, ref);
    }
    private ClubComment locked(Long id, ClubSourceRef ref) {
        var row = comments.lock(id, ref.eventId(), ref.announcementId()).orElseThrow(ClubCommentWorkspace::missing);
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE); source(row, ref); return row;
    }
    public static ResourceNotFoundException missing() { return new ResourceNotFoundException("Yorum bulunamadı."); }
}
