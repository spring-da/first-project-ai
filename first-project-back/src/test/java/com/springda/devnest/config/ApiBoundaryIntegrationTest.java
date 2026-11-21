package com.springda.devnest.config;

import com.springda.devnest.image.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-boundary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "springdoc.api-docs.enabled=true", "app.oss.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItYXBpLWJvdW5kYXJ5LXRlc3Rz",
        "app.security.issuer=devnest-api", "app.security.audience=devnest-clients",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
class ApiBoundaryIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtDecoder jwtDecoder;
    @MockitoBean ImageStorage imageStorage;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void generatedOpenApiDocumentsPublicAndBearerProtectedEndpoints() throws Exception {
        var body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode contract = json.readTree(body);

        assertThat(contract.path("openapi").asText()).startsWith("3.");
        assertThat(contract.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(contract.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(operation(contract, "/api/v1/auth/login", "post").path("security").isEmpty()).isTrue();
        assertThat(operation(contract, "/api/v1/public/share-bundles/{token}", "get").path("security").isEmpty()).isTrue();
        assertThat(operation(contract, "/api/v1/tasks", "get").path("security").isMissingNode()).isTrue();
        assertThat(operation(contract, "/api/v1/sharing/links/{id}/token", "post").isMissingNode()).isFalse();
    }

    @Test
    void apiResponsesExposeStrictHeadersAndCorsDoesNotAllowBrowserCredentials() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));

        mvc.perform(options("/api/v1/tasks")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void decoderRejectsTokensIssuedForAnotherAudience() {
        var now = Instant.now();
        var wrongAudience = JwtClaimsSet.builder()
                .issuer("devnest-api")
                .audience(List.of("another-service"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .subject("test-user")
                .build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), wrongAudience)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private JsonNode operation(JsonNode contract, String path, String method) {
        return contract.path("paths").path(path).path(method);
    }
}
