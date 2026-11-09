package com.campusplus.backend.security;

import com.campusplus.backend.entity.Role;
import com.campusplus.backend.entity.StudentProfile;
import com.campusplus.backend.entity.User;
import com.campusplus.backend.service.messaging.DirectConversationMembers;
import org.springframework.stereotype.Component;

/** Pure eligibility checks. Services must load current state and recheck under locks. */
@Component
public class DirectMessagePolicy {
    public enum ContactState { CLEAR, BLOCKED_BY_ACTOR, BLOCKED_BY_RECIPIENT, BLOCKED_BY_BOTH }

    // Changing university or blocking contact stops new messages, not access to one's history.
    public boolean readable(DirectConversationMembers members, User actor) {
        return members != null && actor != null && members.contains(actor.getId());
    }

    public boolean writable(DirectConversationMembers members, User actor, User recipient,
            StudentProfile actorProfile, StudentProfile recipientProfile, ContactState contact) {
        return readable(members, actor) && recipient != null && members.contains(recipient.getId())
                && !actor.getId().equals(recipient.getId()) && contact == ContactState.CLEAR
                && eligible(actor, actorProfile) && eligible(recipient, recipientProfile)
                && actorProfile.getUniversity().getId().equals(recipientProfile.getUniversity().getId());
    }

    private boolean eligible(User user, StudentProfile profile) {
        return user.getRoles().contains(Role.STUDENT) && profile != null
                && user.getId().equals(profile.getUserId()) && profile.getUniversity() != null
                && profile.getUniversity().getId() != null && profile.getUniversity().isActive();
    }
}
