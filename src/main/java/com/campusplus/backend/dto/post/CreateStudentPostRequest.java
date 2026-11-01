package com.campusplus.backend.dto.post;

import jakarta.validation.constraints.*;

public record CreateStudentPostRequest(@NotBlank @Size(max = 10000) String body) {
    public CreateStudentPostRequest { body = body == null ? null : body.strip(); }
    @Override public String toString() { return "CreateStudentPostRequest[content=[REDACTED]]"; }
}
