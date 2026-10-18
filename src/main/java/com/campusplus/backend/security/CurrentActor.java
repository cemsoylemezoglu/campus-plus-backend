package com.campusplus.backend.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    public boolean isAdmin() {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        return actor != null && actor.isAuthenticated() && actor.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    public long userId() {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (actor != null && actor.isAuthenticated()) {
                long id = Long.parseLong(actor.getName());
                if (id > 0) return id;
            }
        } catch (NumberFormatException ignored) { }
        throw new AuthenticationCredentialsNotFoundException("Kimlik doğrulama gerekli.");
    }
}
