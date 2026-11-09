package com.campusplus.backend.service.messaging;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import jakarta.persistence.*;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component @Transactional(propagation = Propagation.MANDATORY)
public class DirectMessageWorkspace {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final UniversityRepository universities;
    private final UserBlockRepository blocks;
    private final DirectConversationRepository conversations;
    private final CurrentActor current;
    private final DirectMessagePolicy policy;
    private final EntityManager em;

    public DirectMessageWorkspace(UserRepository users, StudentProfileRepository profiles, UniversityRepository universities,
            UserBlockRepository blocks, DirectConversationRepository conversations, CurrentActor current,
            DirectMessagePolicy policy, EntityManager em) {
        this.users = users; this.profiles = profiles; this.universities = universities; this.blocks = blocks;
        this.conversations = conversations; this.current = current; this.policy = policy; this.em = em;
    }
    public record Pair(DirectConversationMembers members, User actor, User recipient) {
        @Override public String toString() { return "MessagingPair[REDACTED]"; }
    }
    public Pair lockPair(long recipientId) {
        writableTransaction(); long actor = current.userId();
        if (recipientId <= 0 || recipientId == actor) throw unavailable();
        var pair = DirectConversationMembers.between(actor, recipientId);
        User lower = lockUser(pair.lowerUserId()), higher = lockUser(pair.higherUserId());
        var people = Map.of(lower.getId(), lower, higher.getId(), higher);
        return new Pair(pair, people.get(actor), people.get(recipientId));
    }
    public void requireWritable(Pair pair) {
        writableTransaction();
        var actorProfile = profile(pair.actor.getId()); var recipientProfile = profile(pair.recipient.getId());
        // User locks serialize profile and role writers. Lock universities only after conversation lock.
        if (actorProfile != null && recipientProfile != null
                && actorProfile.getUniversity().getId().equals(recipientProfile.getUniversity().getId())) {
            var university = universities.findByIdForShare(actorProfile.getUniversity().getId()).orElseThrow(DirectMessageWorkspace::unavailable);
            em.refresh(university, LockModeType.PESSIMISTIC_READ);
        }
        boolean outgoing = blocks.existsByBlockerIdAndBlockedIdAndActiveTrue(pair.actor.getId(), pair.recipient.getId());
        boolean incoming = blocks.existsByBlockerIdAndBlockedIdAndActiveTrue(pair.recipient.getId(), pair.actor.getId());
        var contact = outgoing || incoming ? DirectMessagePolicy.ContactState.BLOCKED_BY_BOTH : DirectMessagePolicy.ContactState.CLEAR;
        if (!policy.writable(pair.members, pair.actor, pair.recipient, actorProfile, recipientProfile, contact)) throw unavailable();
    }
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public DirectConversation readable(long id) {
        var conversation = conversations.findById(id).orElseThrow(DirectMessageWorkspace::missing);
        if (!conversation.members().contains(current.userId())) throw missing();
        return conversation;
    }
    public DirectConversation lockConversation(long id) {
        writableTransaction();
        var conversation = readable(id);
        // A preflight may hold an older version while waiting for the user locks.
        // Refresh under the database lock directly: a locking SELECT first would compare
        // its new version to that stale managed instance before refresh can run.
        em.refresh(conversation, LockModeType.PESSIMISTIC_WRITE);
        if (!conversation.members().contains(current.userId())) throw missing();
        return conversation;
    }
    private User lockUser(long id) {
        var user = users.findByIdForUpdate(id).orElseThrow(DirectMessageWorkspace::unavailable);
        em.refresh(user); return user;
    }
    private StudentProfile profile(long id) {
        var profile = profiles.findById(id).orElse(null); if (profile != null) em.refresh(profile); return profile;
    }
    private void writableTransaction() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalTransactionStateException("Mesaj yazımı için yazılabilir transaction gereklidir.");
    }
    public static ResourceNotFoundException missing() { return new ResourceNotFoundException("Konuşma bulunamadı."); }
    private static ResourceNotFoundException unavailable() { return new ResourceNotFoundException("Mesaj işlemi gerçekleştirilemiyor."); }
}
