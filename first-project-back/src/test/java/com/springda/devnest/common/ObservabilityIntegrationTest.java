package com.springda.devnest.common;

import com.springda.devnest.image.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:observability;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "springdoc.api-docs.enabled=false", "app.oss.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3Itb2JzZXJ2YWJpbGl0eS10ZXN0cw==",
        "app.security.issuer=devnest-api", "app.security.audience=devnest-clients",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
class ObservabilityIntegrationTest {

    @Autowired WebApplicationContext context;
    @MockitoBean ImageStorage imageStorage;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(RequestCorrelationFilter.class))
                .apply(springSecurity())
                .build();
    }

    @Test
    void healthProbesArePublicAndCarryRequestReferences() throws Exception {
        mvc.perform(get("/actuator/health/liveness")
                        .header(RequestCorrelationFilter.HEADER, "probe-liveness-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestCorrelationFilter.HEADER, "probe-liveness-1"))
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestCorrelationFilter.HEADER,
                        matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricsRequireAuthenticationAndValidationProblemsExposeRequestId() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(user("monitor").roles("ADMIN")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RequestCorrelationFilter.HEADER, "support-login-1")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestCorrelationFilter.HEADER, "support-login-1"))
                .andExpect(jsonPath("$.requestId").value("support-login-1"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
