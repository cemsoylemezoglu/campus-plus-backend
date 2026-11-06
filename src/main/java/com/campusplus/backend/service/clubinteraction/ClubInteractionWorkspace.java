package com.campusplus.backend.service.clubinteraction;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.ClubInteractionPolicy;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.persistence.*;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Called by transactional services; acquiring these locks must never create a short-lived separate transaction. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ClubInteractionWorkspace {
    private final ClubWorkspace core;
    private final EventRepository events;
    private final AnnouncementRepository announcements;
    private final ClubInteractionPolicy policy;
    private final EntityManager em;

    public ClubInteractionWorkspace(ClubWorkspace core, EventRepository events, AnnouncementRepository announcements,
            ClubInteractionPolicy policy, EntityManager em) {
        this.core = core;
        this.events = events;
        this.announcements = announcements;
        this.policy = policy;
        this.em = em;
    }

    public record Context(ClubInteractionSource source, User actor, ClubMembership membership, StudentProfile profile) {
        @Override public String toString() { return "ClubInteractionContext[content=[REDACTED]]"; }
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Context readable(ClubSourceRef ref) {
        var context = context(resolve(ref), core.actor());
        requireReadable(context);
        return context;
    }

    public Context participant(ClubSourceRef ref) { return lock(ref, false); }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Context readableManager(ClubSourceRef ref) {
        var context = readable(ref);
        requireCapability(context, true);
        return context;
    }

    public Context resourceManager(ClubSourceRef ref) { return lock(ref, true); }

    /** Caller must first verify ownership of its own comment/like, or ADMIN inspection authority. */
    public Context cleanup(ClubSourceRef ref) { return lockSourceContext(ref, false, false); }

    private Context lock(ClubSourceRef ref, boolean management) {
        return lockSourceContext(ref, true, management);
    }

    private Context lockSourceContext(ClubSourceRef ref, boolean requireAccess, boolean management) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalTransactionStateException("Etkileşim yazımı için yazılabilir transaction gereklidir.");
        var preview = requireAccess ? readable(ref) : context(resolve(ref), core.actor());
        // Preflight rejects unauthorized requests before taking source locks. Repeat after refresh below.
        if (requireAccess) requireCapability(preview, management);
        long clubId = preview.source().club().getId();
        long universityId = preview.source().club().getUniversity().getId();
        User actor = core.lockUser(preview.actor().getId());
        Club club = core.clubs.findByIdForShare(clubId).orElseThrow(ClubInteractionWorkspace::missing);
        em.refresh(club, LockModeType.PESSIMISTIC_READ);
        // Match Event/Announcement writers: actor -> club SHARE -> source WRITE -> university SHARE.
        var source = lockSource(ref, clubId);
        if (!source.ref().equals(ref) || !Objects.equals(source.club().getId(), clubId)
                || !Objects.equals(club.getUniversity().getId(), universityId)
                || !Objects.equals(source.club().getUniversity().getId(), universityId)) throw missing();
        University university = core.universities.findByIdForShare(universityId)
                .orElseThrow(ClubInteractionWorkspace::missing);
        core.refreshUniversity(university);
        var locked = context(source, actor);
        if (requireAccess) {
            requireReadable(locked);
            requireCapability(locked, management);
        }
        return locked;
    }

    private ClubInteractionSource resolve(ClubSourceRef ref) {
        return switch (ref.kind()) {
            case EVENT -> new ClubInteractionSource.EventSource(events.findById(ref.id()).orElseThrow(ClubInteractionWorkspace::missing));
            case ANNOUNCEMENT -> new ClubInteractionSource.AnnouncementSource(announcements.findById(ref.id()).orElseThrow(ClubInteractionWorkspace::missing));
        };
    }

    private ClubInteractionSource lockSource(ClubSourceRef ref, long clubId) {
        return switch (ref.kind()) {
            case EVENT -> {
                Event event = events.findForUpdate(ref.id(), clubId).orElseThrow(ClubInteractionWorkspace::missing);
                em.refresh(event, LockModeType.PESSIMISTIC_WRITE);
                yield new ClubInteractionSource.EventSource(event);
            }
            case ANNOUNCEMENT -> {
                Announcement announcement = announcements.findForUpdate(ref.id(), clubId).orElseThrow(ClubInteractionWorkspace::missing);
                em.refresh(announcement, LockModeType.PESSIMISTIC_WRITE);
                // An announcement's linked event is neither locked nor exposed by this adapter.
                yield new ClubInteractionSource.AnnouncementSource(announcement);
            }
        };
    }

    private Context context(ClubInteractionSource source, User actor) {
        StudentProfile profile = core.profiles.findById(actor.getId()).orElse(null);
        if (profile != null) em.refresh(profile);
        return new Context(source, actor, core.local(source.club(), actor.getId()), profile);
    }

    private void requireReadable(Context c) {
        if (!c.source().readable(policy, c.actor(), c.membership(), c.profile())) throw missing();
    }

    private void requireCapability(Context c, boolean management) {
        boolean allowed = management
                ? c.source().resourceManager(policy, c.actor(), c.membership(), c.profile())
                : c.source().writable(policy, c.actor(), c.membership(), c.profile());
        if (!allowed) throw new AccessDeniedException("Bu işlem için yetkiniz bulunmuyor.");
    }

    private static ResourceNotFoundException missing() { return new ResourceNotFoundException("İçerik bulunamadı."); }
}
