package com.campusplus.backend.service;

import com.campusplus.backend.config.JwtConfig;
import com.campusplus.backend.dto.LoginResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, Clock jwtClock) {
        this.encoder = encoder;
        this.clock = jwtClock;
    }

    public LoginResponse issueAccessToken(Long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE)).subject(userId.toString())
                .issuedAt(now).expiresAt(now.plus(JwtConfig.ACCESS_TOKEN_TTL))
                .id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", JwtConfig.ACCESS_TOKEN_TTL.toSeconds());
    }
}
