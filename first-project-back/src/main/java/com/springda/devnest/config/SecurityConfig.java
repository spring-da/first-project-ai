package com.springda.devnest.config;

import com.springda.devnest.common.RequestCorrelationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.util.Base64;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountSecurityFilter accountSecurityFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .referrerPolicy(referrerPolicy -> referrerPolicy
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissionsPolicy -> permissionsPolicy
                                .policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .contentSecurityPolicy(contentSecurityPolicy -> contentSecurityPolicy
                                .policyDirectives("default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31_536_000)
                                .includeSubDomains(false)
                                .preload(false)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/display-name-availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/markdown-shares/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/knowledge-shares/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/share-bundles/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/info", "/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/password", "/api/v1/auth/logout-all").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .addFilterAfter(accountSecurityFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtSecretKey(AppProperties properties) {
        if (properties.security().issuer() == null || properties.security().issuer().isBlank()) {
            throw new IllegalStateException("JWT_ISSUER 不能为空");
        }
        if (properties.security().audience() == null || properties.security().audience().isBlank()) {
            throw new IllegalStateException("JWT_AUDIENCE 不能为空");
        }
        if (properties.security().accessTokenTtl() == null
                || properties.security().accessTokenTtl().isZero()
                || properties.security().accessTokenTtl().isNegative()) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_TTL 必须大于 0");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.security().jwtSecretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 必须是合法的 Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 解码后不能少于 32 字节");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey, AppProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(properties.security().issuer());
        var audienceValidator = new JwtClaimValidator<List<String>>("aud",
                audience -> audience != null && audience.contains(properties.security().audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        validateAllowedOrigins(properties.cors().allowedOrigins());
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", WorkspaceOwnerResolver.HEADER,
                RequestCorrelationFilter.HEADER));
        configuration.setExposedHeaders(List.of(
                "Location", "Content-Disposition", RequestCorrelationFilter.HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void validateAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS 至少需要一个明确来源");
        }
        for (String origin : allowedOrigins) {
            try {
                var uri = URI.create(origin);
                var scheme = uri.getScheme();
                var path = uri.getPath();
                if (origin.isBlank() || "*".equals(origin)
                        || (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
                        || uri.getHost() == null || uri.getUserInfo() != null
                        || (path != null && !path.isEmpty())
                        || uri.getQuery() != null || uri.getFragment() != null) {
                    throw new IllegalArgumentException("invalid origin");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS 只能包含不带路径和末尾斜杠的 HTTP(S) 来源: " + origin,
                        exception);
            }
        }
    }
}
