package com.springda.devnest.share;

import com.jayway.jsonpath.JsonPath;
import com.springda.devnest.log.DevLogDtos;
import com.springda.devnest.log.DevLogService;
import com.springda.devnest.log.LogCategory;
import com.springda.devnest.markdown.MarkdownShareTokenService;
import com.springda.devnest.snippet.SnippetDtos;
import com.springda.devnest.snippet.SnippetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:knowledge-shares;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3Ita25vd2xlZGdlLXNoYXJlLXRlc3Rz",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173", "app.oss.enabled=false"
})
@Transactional
class KnowledgeShareIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired SnippetService snippets;
    @Autowired DevLogService logs;
    @Autowired KnowledgeShareService shares;
    @Autowired KnowledgeShareRepository repository;
    @Autowired MarkdownShareTokenService tokens;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void snippetOwnerCreatesListsAndRevokesAOneTimePublicLink() throws Exception {
        var snippet = createSnippet("owner-one");
        var expiresAt = Instant.now().plus(Duration.ofDays(7));

        mvc.perform(post("/api/v1/snippets/{id}/shares", snippet.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + expiresAt + "\"}"))
                .andExpect(status().isUnauthorized());

        var body = mvc.perform(post("/api/v1/snippets/{id}/shares", snippet.id())
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + expiresAt + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isString())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        String shareId = JsonPath.read(body, "$.id");
        assertThat(token).hasSize(43);
        assertThat(body).doesNotContain("tokenDigest", "owner-one");
        assertThat(repository.findByTokenDigest(tokens.digest(token))).isPresent();

        mvc.perform(get("/api/v1/public/knowledge-shares/{token}", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resourceType").value("SNIPPET"))
                .andExpect(jsonPath("$.title").value("Cursor example"))
                .andExpect(jsonPath("$.language").value("TypeScript"))
                .andExpect(jsonPath("$.content").value("const cursor = 'next';"));

        mvc.perform(get("/api/v1/snippets/{id}/shares", snippet.id())
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shareId))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].token").doesNotExist());

        mvc.perform(delete("/api/v1/snippets/{resourceId}/shares/{shareId}", snippet.id(), shareId)
                        .with(jwt().jwt(jwt -> jwt.subject("owner-two"))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/snippets/{resourceId}/shares/{shareId}", snippet.id(), shareId)
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/knowledge-shares/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("分享链接不存在、已过期或已被撤销"));
    }

    @Test
    void devLogCanBeSharedAndTrashedOrExpiredContentCannotBeRead() throws Exception {
        var log = createLog("owner-one");
        var response = shares.create("owner-one", KnowledgeResourceType.DEV_LOG, log.id(),
                new KnowledgeShareDtos.CreateRequest(Instant.now().plus(Duration.ofDays(2))));

        mvc.perform(get("/api/v1/public/knowledge-shares/{token}", response.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("DEV_LOG"))
                .andExpect(jsonPath("$.title").value("Share rollout"))
                .andExpect(jsonPath("$.category").value("DECISION"))
                .andExpect(jsonPath("$.tags[0]").value("release"))
                .andExpect(jsonPath("$.content").value("Publish the read-only link after verification."));

        logs.delete("owner-one", log.id());
        mvc.perform(get("/api/v1/public/knowledge-shares/{token}", response.token()))
                .andExpect(status().isNotFound());

        var snippet = createSnippet("owner-one");
        var expiredToken = tokens.createToken();
        repository.saveAndFlush(new KnowledgeShareEntity(
                KnowledgeResourceType.SNIPPET, snippet.id(), "owner-one",
                tokens.digest(expiredToken), Instant.now().minusSeconds(1)));
        mvc.perform(get("/api/v1/public/knowledge-shares/{token}", expiredToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/knowledge-shares/not-a-token"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/snippets/{id}/shares", snippet.id())
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + Instant.now().plus(Duration.ofDays(366)) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("分享链接的有效期不能超过 365 天。"));
    }

    private SnippetDtos.Response createSnippet(String ownerId) {
        return snippets.create(ownerId, new SnippetDtos.SaveRequest(
                "Cursor example", "TypeScript", "const cursor = 'next';", false, null));
    }

    private DevLogDtos.Response createLog(String ownerId) {
        return logs.create(ownerId, new DevLogDtos.SaveRequest(
                "Share rollout", "Publish the read-only link after verification.",
                LogCategory.DECISION, List.of("release", "sharing"), true, null));
    }
}
