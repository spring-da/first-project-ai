package com.springda.devnest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Cors cors, AccountSecurity accountSecurity) {

    public record Security(String issuer, Duration accessTokenTtl, String jwtSecretBase64) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record AccountSecurity(
            Duration invitationTokenTtl,
            Duration temporaryPasswordTtl,
            int loginFailureThreshold,
            Duration loginLockDuration,
            int loginRateLimitAttempts,
            Duration loginRateLimitWindow,
            int saveRateLimitAttempts,
            Duration saveRateLimitWindow,
            boolean bootstrapAdminRequired,
            String bootstrapAdminEmail,
            String bootstrapAdminPassword
    ) {
    }
}
