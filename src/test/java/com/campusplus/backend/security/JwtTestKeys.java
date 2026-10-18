package com.campusplus.backend.security;

import java.security.SecureRandom;
import java.util.Base64;

public final class JwtTestKeys {
    private JwtTestKeys() {}

    public static String newSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
