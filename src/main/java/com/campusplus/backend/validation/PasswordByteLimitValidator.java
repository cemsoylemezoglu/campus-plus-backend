package com.campusplus.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordByteLimitValidator implements ConstraintValidator<PasswordByteLimit, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/blank values are handled by @NotBlank. BCrypt's limit is bytes, not characters.
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
