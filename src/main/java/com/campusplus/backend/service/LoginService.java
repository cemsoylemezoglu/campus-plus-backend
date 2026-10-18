package com.campusplus.backend.service;

import com.campusplus.backend.dto.LoginRequest;
import com.campusplus.backend.dto.LoginResponse;
import com.campusplus.backend.entity.User;
import com.campusplus.backend.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class LoginService {
    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final String dummyHash;

    public LoginService(UserRepository repository, PasswordEncoder encoder, JwtTokenService tokens) {
        this.repository = repository;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(@Valid LoginRequest request) {
        User user = repository.findByEmail(request.email()).orElse(null);
        String hash = user == null ? null : user.getPasswordHash();
        // Unknown and legacy accounts still perform a BCrypt check to reduce timing differences.
        boolean matches = encoder.matches(request.password(), hash == null ? dummyHash : hash);
        if (user == null || hash == null || !matches) {
            throw new BadCredentialsException("E-posta veya parola hatalı.");
        }
        return tokens.issueAccessToken(user.getId());
    }
}
