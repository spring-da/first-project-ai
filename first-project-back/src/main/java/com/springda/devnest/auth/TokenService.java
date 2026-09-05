package com.springda.devnest.auth;

import com.springda.devnest.config.AppProperties;
import com.springda.devnest.user.UserEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties properties;

    public TokenService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String createAccessToken(UserEntity user) {
        var now = Instant.now();
        var expiresAt = now.plus(properties.security().accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.security().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("scope", "user")
                .claim("role", user.getRole().name())
                .claim("must_change_password", user.isForcePasswordChange())
                .claim("auth_version", user.getAuthVersion())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return properties.security().accessTokenTtl().toSeconds();
    }
}
