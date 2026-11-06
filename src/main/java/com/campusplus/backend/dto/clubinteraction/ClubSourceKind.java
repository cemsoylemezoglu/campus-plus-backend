package com.campusplus.backend.dto.clubinteraction;

/** Stable cursor tags; never use enum ordinals in serialized cursors. */
public enum ClubSourceKind {
    EVENT(1), ANNOUNCEMENT(2);

    private final byte cursorTag;

    ClubSourceKind(int cursorTag) { this.cursorTag = (byte) cursorTag; }

    public byte cursorTag() { return cursorTag; }

    public static ClubSourceKind fromCursorTag(byte tag) {
        for (var kind : values()) if (kind.cursorTag == tag) return kind;
        throw new IllegalArgumentException("Unknown source kind");
    }
}
