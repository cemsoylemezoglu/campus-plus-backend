package com.campusplus.backend.validation;

import jakarta.validation.*;
import java.net.URI;

public class HttpWebsiteValidator implements ConstraintValidator<HttpWebsite, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException exception) { return false; }
    }
}
