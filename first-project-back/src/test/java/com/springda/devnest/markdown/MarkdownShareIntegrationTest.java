package com.springda.devnest.markdown;

import com.jayway.jsonpath.JsonPath;
import com.springda.devnest.image.ImageStorage;
import com.springda.devnest.image.MarkdownImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:markdown-shares;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItbWFya2Rvd24tc2hhcmUtdGVzdHM=",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173", "app.oss.enabled=false",
        "app.oss.prefix=markdown/images"
})
@Transactional
class MarkdownShareIntegrationTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");

    @Autowired WebApplicationContext context;
    @Autowired MarkdownDocumentService documents;
    @Autowired MarkdownShareService shares;
    @Autowired MarkdownShareRepository shareRepository;
    @Autowired MarkdownShareTokenService tokens;
    @Autowired MarkdownImageService images;
    @MockitoBean ImageStorage storage;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void ownerCreatesListsAndRevokesAOneTimePublicLink() throws Exception {
        var document = createDocument("owner-one", "# Shared note\n\nVisible without an account.");
        var expiresAt = Instant.now().plus(Duration.ofDays(7));

        mvc.perform(post("/api/v1/markdown-documents/{id}/shares", document.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + expiresAt + "\"}"))
                .andExpect(status().isUnauthorized());

        var body = mvc.perform(post("/api/v1/markdown-documents/{id}/shares", document.id())
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
        assertThat(shareRepository.findByTokenDigest(tokens.digest(token))).isPresent();

        mvc.perform(get("/api/v1/public/markdown-shares/{token}", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Shared note"))
                .andExpect(jsonPath("$.content").value("# Shared note\n\nVisible without an account."))
                .andExpect(jsonPath("$.expiresAt").isString());

        mvc.perform(get("/api/v1/markdown-documents/{id}/shares", document.id())
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shareId))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].token").doesNotExist());

        mvc.perform(delete("/api/v1/markdown-documents/{documentId}/shares/{shareId}", document.id(), shareId)
                        .with(jwt().jwt(jwt -> jwt.subject("owner-two"))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/markdown-documents/{documentId}/shares/{shareId}", document.id(), shareId)
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/markdown-shares/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("分享链接不存在、已过期或已被撤销"));
    }

    @Test
    void expiredTrashedInvalidAndExcessivelyLongSharesCannotBeRead() throws Exception {
        var document = createDocument("owner-one", "Private content");
        var expiredToken = tokens.createToken();
        shareRepository.saveAndFlush(new MarkdownShareEntity(
                document.id(), "owner-one", tokens.digest(expiredToken), Instant.now().minusSeconds(1)));

        mvc.perform(get("/api/v1/public/markdown-shares/{token}", expiredToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/markdown-shares/not-a-valid-token")).andExpect(status().isNotFound());

        var valid = shares.create("owner-one", document.id(),
                new MarkdownShareDtos.CreateRequest(Instant.now().plus(Duration.ofDays(1))));
        documents.delete("owner-one", document.id());
        mvc.perform(get("/api/v1/public/markdown-shares/{token}", valid.token())).andExpect(status().isNotFound());

        var other = createDocument("owner-one", "Other");
        mvc.perform(post("/api/v1/markdown-documents/{id}/shares", other.id())
                        .with(jwt().jwt(jwt -> jwt.subject("owner-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + Instant.now().plus(Duration.ofDays(366)) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("分享链接的有效期不能超过 365 天。"));
    }

    @Test
    void publicLinkReadsOnlyImagesReferencedByTheSharedDocument() throws Exception {
        var used = images.upload("owner-one", "used.png", PNG);
        var unused = images.upload("owner-one", "unused.png", PNG);
        var document = createDocument("owner-one",
                "# Illustrated\n\n![diagram](/api/v1/markdown-images/" + used.id() + ")");
        var share = shares.create("owner-one", document.id(),
                new MarkdownShareDtos.CreateRequest(Instant.now().plus(Duration.ofDays(1))));
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1)).write(PNG);
            return null;
        }).when(storage).transferTo(anyString(), any(java.io.OutputStream.class));
        clearInvocations(storage);

        var download = mvc.perform(get("/api/v1/public/markdown-shares/{token}/images/{id}", share.token(), used.id()))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(download))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        clearInvocations(storage);
        mvc.perform(get("/api/v1/public/markdown-shares/{token}/images/{id}", share.token(), unused.id()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    private MarkdownDocumentDtos.Response createDocument(String ownerId, String content) {
        return documents.create(ownerId,
                new MarkdownDocumentDtos.CreateRequest("Shared note", "shared.md", content, null, false));
    }
}
