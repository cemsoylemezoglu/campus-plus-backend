package com.campusplus.backend.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class JwtConfig {
    public static final String ISSUER = "https://campus-plus.local";
    public static final String AUDIENCE = "campus-plus-api";
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    @Bean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey jwtSigningKey(Environment environment) {
        String encoded = environment.getProperty("JWT_SECRET");
        byte[] key;
        try {
            key = encoded == null ? new byte[0] : Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException("JWT_SECRET must be Base64-encoded and contain at least 32 random bytes");
        }
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be Base64-encoded and contain at least 32 random bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, Clock jwtClock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(jwtClock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && !jwt.getIssuedAt().isAfter(jwtClock.instant())
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && jwt.getExpiresAt().isAfter(jwtClock.instant())
                    && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                    && jwt.getSubject() != null && jwt.getSubject().matches("[1-9][0-9]*");
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamp, new JwtIssuerValidator(ISSUER), claims));
        return decoder;
    }
}
