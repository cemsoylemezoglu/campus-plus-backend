package com.campusplus.backend.security;

import com.campusplus.backend.entity.User;
import com.campusplus.backend.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository repository;

    public DatabaseJwtAuthenticationConverter(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("Geçersiz kimlik doğrulama bilgileri.");
        }
        User user = repository.findById(id)
                .orElseThrow(() -> new InvalidBearerTokenException("Geçersiz kimlik doğrulama bilgileri."));
        if (user.getPasswordHash() == null || user.getRoles().isEmpty()) {
            throw new InvalidBearerTokenException("Geçersiz kimlik doğrulama bilgileri.");
        }
        // Never trust roles/scope supplied by the client or stale token claims.
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
        return new JwtAuthenticationToken(jwt, authorities, Long.toString(id));
    }
}
