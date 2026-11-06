package com.campusplus.backend.dto.clubinteraction;

import com.campusplus.backend.exception.InvalidRequestException;

/** Event and announcement IDs are separate namespaces, even when their numbers match. */
public record ClubSourceRef(ClubSourceKind kind, long id) {
    public ClubSourceRef {
        if (kind == null || id <= 0) throw new InvalidRequestException("Geçerli bir etkileşim kaynağı gereklidir.");
    }
    public Long eventId() { return kind == ClubSourceKind.EVENT ? id : null; }
    public Long announcementId() { return kind == ClubSourceKind.ANNOUNCEMENT ? id : null; }
}
