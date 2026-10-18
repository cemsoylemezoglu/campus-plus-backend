package com.campusplus.backend.dto.academic;

import java.util.Locale;

final class AcademicText {
    private AcademicText() {}
    static String strip(String value) { return value == null ? null : value.strip(); }
    static String upper(String value) { return value == null ? null : value.strip().toUpperCase(Locale.ROOT); }
    static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
