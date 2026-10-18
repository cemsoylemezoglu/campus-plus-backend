package com.campusplus.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("userAccess")
public class UserAccessPolicy {
    public boolean canAccess(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated() || userId == null) return false;
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return admin || (authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STUDENT")
                        || authority.getAuthority().equals("ROLE_CLUB_ADMIN"))
                && authentication.getName().equals(userId.toString()));
    }
}
