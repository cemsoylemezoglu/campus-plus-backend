package com.campusplus.backend.dto.club;

import java.time.Instant;
public record ClubReviewResponse(ClubResponse club, String reviewNote, Long reviewedBy, Instant reviewedAt) {}
