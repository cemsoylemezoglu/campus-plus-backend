package com.campusplus.backend.service.clubinteraction;

import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.security.ClubInteractionPolicy;
import java.util.Objects;

/** Explicit adapters keep ordinary social access separate from management detail access. */
public sealed interface ClubInteractionSource {
    ClubSourceRef ref();
    Club club();
    default Event event() { return null; }
    default Announcement announcement() { return null; }
    boolean readable(ClubInteractionPolicy policy, User actor, ClubMembership membership, StudentProfile profile);
    boolean writable(ClubInteractionPolicy policy, User actor, ClubMembership membership, StudentProfile profile);
    boolean resourceManager(ClubInteractionPolicy policy, User actor, ClubMembership membership, StudentProfile profile);

    record EventSource(Event event) implements ClubInteractionSource {
        public EventSource { Objects.requireNonNull(event); }
        public ClubSourceRef ref() { return new ClubSourceRef(ClubSourceKind.EVENT, event.getId()); }
        public Club club() { return event.getClub(); }
        public boolean readable(ClubInteractionPolicy p, User a, ClubMembership m, StudentProfile s) { return p.readable(event, a, m, s); }
        public boolean writable(ClubInteractionPolicy p, User a, ClubMembership m, StudentProfile s) { return p.writable(event, a, m, s); }
        public boolean resourceManager(ClubInteractionPolicy p, User a, ClubMembership m, StudentProfile s) { return p.resourceManager(event, a, m, s); }
        @Override public String toString() { return "EventSource[content=[REDACTED]]"; }
    }

    record AnnouncementSource(Announcement announcement) implements ClubInteractionSource {
        public AnnouncementSource { Objects.requireNonNull(announcement); }
        public ClubSourceRef ref() { return new ClubSourceRef(ClubSourceKind.ANNOUNCEMENT, announcement.getId()); }
        public Club club() { return announcement.getClub(); }
        public boolean readable(ClubInteractionPolicy p, User a, ClubMembership m, StudentProfile s) { return p.readable(announcement, a, m, s); }
        public boolean writable(ClubInteractionPolicy p, User a, ClubMembership m, StudentProfile s) { return p.writable(announcement, a, m, s); }
        public boolean resourceManager(ClubInteractionPolicy p, User a, ClubMembership m, StudentProfile s) { return p.resourceManager(announcement, a, m, s); }
        @Override public String toString() { return "AnnouncementSource[content=[REDACTED]]"; }
    }
}
