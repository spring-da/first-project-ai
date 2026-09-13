package com.springda.devnest.sharing;

import com.jayway.jsonpath.JsonPath;
import com.springda.devnest.markdown.*;
import com.springda.devnest.snippet.*;
import com.springda.devnest.flowchart.*;
import com.springda.devnest.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sharing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "app.oss.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3Ita25vd2xlZGdlLXNoYXJlLXRlc3Rz",
        "app.account-security.bootstrap-admin-required=false", "app.cors.allowed-origins=http://localhost:5173"
})
@Transactional
class SharingIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired MarkdownDocumentService markdown;
    @Autowired SnippetService snippets;
    @Autowired FlowchartService logs;
    @Autowired UserRepository users;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MarkdownShareService legacyMarkdown;
    @Autowired com.springda.devnest.share.KnowledgeShareService legacyKnowledge;
    @Autowired com.springda.devnest.image.MarkdownImageService images;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired com.springda.devnest.auth.TokenService authTokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.springda.devnest.image.ImageStorage storage;
    MockMvc mvc;
    String owner;
    String reader;
    String document;
    String snippet;
    String log;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(SharingCacheFilter.class)).apply(springSecurity()).build();
        owner = users.saveAndFlush(new UserEntity("author@example.test", "hash", "Ada Author")).getId();
        reader = users.saveAndFlush(new UserEntity("reader@example.test", "hash", "Reader")).getId();
        document = markdown.create(owner, new MarkdownDocumentDtos.CreateRequest("Unicode 中文", "note.md", "needle-only-in-body", null, false)).id();
        snippet = snippets.create(owner, new SnippetDtos.SaveRequest("Cursor", "Java", "return 42;", false, null)).id();
        log = logs.create(owner, new FlowchartDtos.CreateRequest("Rollout", null, false, json.readTree("{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}"), java.util.UUID.randomUUID())).id();
    }

    @Test void mixedPoolIsIdempotentSearchablePagedAndAuthenticated() throws Exception {
        mvc.perform(get("/api/v1/sharing/pool")).andExpect(status().isUnauthorized());
        publish(mixed()).andExpect(status().isCreated()).andExpect(jsonPath("$.publishedCount").value(3));
        publish(mixed()).andExpect(status().isCreated()).andExpect(jsonPath("$.existingCount").value(3));
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader))).param("size", "2"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].mine").value(false)).andExpect(jsonPath("$.items[0].content").doesNotExist());
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader))).param("q", "NEEDLE-only"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].resourceType").value("MARKDOWN"));
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader))).param("q", "Ada").param("type", "SNIPPET"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader))).param("mine", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void batchOwnershipFailurePublishesNothing() throws Exception {
        String foreign = snippets.create(reader, new SnippetDtos.SaveRequest("Private", "Java", "secret", false, null)).id();
        publish("[" + ref("MARKDOWN", document) + "," + ref("SNIPPET", foreign) + "]").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/v1/sharing/links").with(jwt().jwt(j -> j.subject(owner))).contentType(MediaType.APPLICATION_JSON)
                .content(bundle("[" + ref("MARKDOWN", document) + "," + ref("SNIPPET", foreign) + "]")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/links").with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void publicBundleUsesSummariesAndLiveDetailWithIndependentRemovalAndRevocation() throws Exception {
        publish(mixed()).andExpect(status().isCreated());
        String created = createLink(mixed());
        String token = JsonPath.read(created, "$.token"), id = JsonPath.read(created, "$.id");
        assertThat(token).hasSize(43);
        String list = mvc.perform(get("/api/v1/public/share-bundles/{token}", token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(3)).andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String item = JsonPath.read(list, "$.items[0].id");
        String snippetItem = JsonPath.read(list, "$.items[1].id"), logItem = JsonPath.read(list, "$.items[2].id");
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}", token, snippetItem))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resourceType").value("SNIPPET"))
                .andExpect(jsonPath("$.language").value("Java")).andExpect(jsonPath("$.content").value("return 42;"));
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}", token, logItem))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resourceType").value("FLOWCHART"))
                .andExpect(jsonPath("$.diagram.schemaVersion").value(1));
        var current = markdown.get(owner, document);
        markdown.update(owner, document, new MarkdownDocumentDtos.UpdateRequest("Saved title", "note.md", "latest saved", null, false, current.version()));
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}", token, item))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("latest saved"));
        mvc.perform(get("/api/v1/sharing/links/{id}", id).with(jwt().jwt(j -> j.subject(reader)))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/sharing/links/{id}/items/{item}", id, item).with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}", token, item)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(delete("/api/v1/sharing/links/{id}", id).with(jwt().jwt(j -> j.subject(owner)))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader)))).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v1/sharing/links").with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(jsonPath("$.items[0].token").doesNotExist()).andExpect(jsonPath("$.items[0].active").value(false));
    }

    @Test void deletedAndRestoredResourcesRequireExplicitRepublication() throws Exception {
        publish(mixed()).andExpect(status().isCreated());
        String token = JsonPath.read(createLink(mixed()), "$.token");
        markdown.delete(owner, document); snippets.delete(owner, snippet); logs.delete(owner, log);
        markdown.restoreFromTrash(owner, document); snippets.restore(owner, snippet); logs.restore(owner, log);
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader)))).andExpect(jsonPath("$.total").value(0));
        publish(mixed()).andExpect(status().isCreated()).andExpect(jsonPath("$.publishedCount").value(3));
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
    }

    @Test void disabledAuthorIsExcludedFromPoolAndPublicAccess() throws Exception {
        publish(mixed()).andExpect(status().isCreated());
        String token = JsonPath.read(createLink(mixed()), "$.token");
        var user = users.findById(owner).orElseThrow(); user.setEnabled(false); users.saveAndFlush(user);
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(reader)))).andExpect(jsonPath("$.total").value(0));
    }

    @Test void invalidBatchPageAndExpiryAreRejected() throws Exception {
        publish("[]").andExpect(status().isBadRequest());
        publish("[" + ref("MARKDOWN", document) + "," + ref("MARKDOWN", document) + "]").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sharing/pool").param("size", "101").with(jwt().jwt(j -> j.subject(owner)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sharing/links").with(jwt().jwt(j -> j.subject(owner))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"bad\",\"items\":" + mixed() + ",\"expiresAt\":\"" + Instant.now().plus(366, ChronoUnit.DAYS) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/share-bundles/not-a-token")).andExpect(status().isNotFound());
    }

    @Test void legacyLinksDoNotReviveAfterRestoreAndDisabledAuthorsCannotBeRead() throws Exception {
        var expires = Instant.now().plus(7, ChronoUnit.DAYS);
        var oldMarkdown = legacyMarkdown.create(owner, document, new MarkdownShareDtos.CreateRequest(expires));
        var oldSnippet = legacyKnowledge.create(owner, com.springda.devnest.share.KnowledgeResourceType.SNIPPET, snippet,
                new com.springda.devnest.share.KnowledgeShareDtos.CreateRequest(expires));
        markdown.delete(owner, document); snippets.delete(owner, snippet);
        markdown.restoreFromTrash(owner, document); snippets.restore(owner, snippet);
        mvc.perform(get("/api/v1/markdown-documents/{id}/shares", document).with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(get("/api/v1/snippets/{id}/shares", snippet).with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(get("/api/v1/public/markdown-shares/{token}", oldMarkdown.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/knowledge-shares/{token}", oldSnippet.token())).andExpect(status().isNotFound());
        var republished = legacyMarkdown.create(owner, document, new MarkdownShareDtos.CreateRequest(expires));
        mvc.perform(get("/api/v1/public/markdown-shares/{token}", republished.token())).andExpect(status().isOk());
        var user = users.findById(owner).orElseThrow(); user.setEnabled(false); users.saveAndFlush(user);
        mvc.perform(get("/api/v1/public/markdown-shares/{token}", republished.token())).andExpect(status().isNotFound());
    }

    @Test void poolRevocationIsAtomicOwnerOnlyIdempotentAndIndependentFromBundle() throws Exception {
        publish(mixed()).andExpect(status().isCreated());
        String token = JsonPath.read(createLink(mixed()), "$.token");
        String body = mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(owner))))
                .andReturn().getResponse().getContentAsString();
        String poolId = JsonPath.read(body, "$.items[0].id");
        mvc.perform(post("/api/v1/sharing/pool/revoke").with(jwt().jwt(j -> j.subject(owner))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + poolId + "\",\"missing\"]}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/pool/{id}", poolId).with(jwt().jwt(j -> j.subject(reader))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isString());
        mvc.perform(post("/api/v1/sharing/pool/revoke").with(jwt().jwt(j -> j.subject(reader))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + poolId + "\"]}")).andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/sharing/pool/revoke").with(jwt().jwt(j -> j.subject(owner)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + poolId + "\"]}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sharing/pool/{id}", poolId).with(jwt().jwt(j -> j.subject(reader))))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test void batchLinkRevocationIsAtomicAndLastItemRemovalClosesDirectory() throws Exception {
        String first = createLink("[" + ref("MARKDOWN", document) + "]");
        String id = JsonPath.read(first, "$.id"), token = JsonPath.read(first, "$.token");
        mvc.perform(post("/api/v1/sharing/links/revoke").with(jwt().jwt(j -> j.subject(owner))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\",\"missing\"]}")).andExpect(status().isNotFound());
        String directory = mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(directory, "$.items[0].id");
        String otherId = JsonPath.read(createLink(mixed()), "$.id");
        mvc.perform(delete("/api/v1/sharing/links/{id}/items/{item}", otherId, itemId).with(jwt().jwt(j -> j.subject(owner)))).andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++) mvc.perform(delete("/api/v1/sharing/links/{id}/items/{item}", id, itemId).with(jwt().jwt(j -> j.subject(owner)))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/links/{id}", id).with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(jsonPath("$.link.active").value(false)).andExpect(jsonPath("$.link.itemCount").value(0))
                .andExpect(jsonPath("$.items[0].removedAt").isString()).andExpect(jsonPath("$.items[0].available").value(false));
        mvc.perform(post("/api/v1/sharing/links/revoke").with(jwt().jwt(j -> j.subject(owner))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\",\"" + otherId + "\"]}")).andExpect(status().isNoContent());
    }

    @Test void expirationAndOwnerDeletionInvalidateExistingTokenWithoutLeakingItsDigest() throws Exception {
        String created = createLink(mixed());
        String id = JsonPath.read(created, "$.id"), token = JsonPath.read(created, "$.token");
        String digest = jdbc.queryForObject("SELECT token_digest FROM share_bundles WHERE id = ?", String.class, id);
        assertThat(digest).hasSize(64).isNotEqualTo(token);
        jdbc.update("UPDATE share_bundles SET expires_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), id);
        entities.clear();
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
        String liveToken = JsonPath.read(createLink(mixed()), "$.token");
        users.deleteById(owner); users.flush(); entities.clear();
        mvc.perform(get("/api/v1/public/share-bundles/{token}", liveToken)).andExpect(status().isNotFound());
    }

    @Test void imageReadsRequireCurrentReferenceMatchingOwnerAndSelectedItem() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");
        var image = images.upload(owner, "diagram.png", png);
        var unused = images.upload(owner, "unused.png", png);
        var foreign = images.upload(reader, "private.png", png);
        var current = markdown.get(owner, document);
        markdown.update(owner, document, new MarkdownDocumentDtos.UpdateRequest("Illustrated", "note.md", "![diagram](" + image.url() + ")\n![foreign](" + foreign.url() + ")", null, false, current.version()));
        publish("[" + ref("MARKDOWN", document) + "]").andExpect(status().isCreated());
        String poolBody = mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(owner)))).andReturn().getResponse().getContentAsString();
        String poolId = JsonPath.read(poolBody, "$.items[0].id");
        String token = JsonPath.read(createLink(mixed()), "$.token");
        String directory = mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andReturn().getResponse().getContentAsString();
        String item = JsonPath.read(directory, "$.items[0].id"), otherItem = JsonPath.read(directory, "$.items[1].id");
        org.mockito.Mockito.doAnswer(call -> { ((java.io.OutputStream) call.getArgument(1)).write(png); return null; })
                .when(storage).transferTo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(java.io.OutputStream.class));
        var download = mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}/images/{image}", token, item, image.id()))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(download)).andExpect(status().isOk()).andExpect(content().bytes(png)).andExpect(header().string("Cache-Control", "no-store"));
        var poolDownload = mvc.perform(get("/api/v1/sharing/pool/{id}/images/{image}", poolId, image.id()).with(jwt().jwt(j -> j.subject(reader))))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(poolDownload)).andExpect(status().isOk()).andExpect(content().bytes(png));
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}/images/{image}", token, item, unused.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}/images/{image}", token, item, foreign.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}/images/{image}", token, otherItem, image.id())).andExpect(status().isNotFound());
        var updated = markdown.get(owner, document);
        markdown.update(owner, document, new MarkdownDocumentDtos.UpdateRequest("Illustrated", "note.md", "image removed", null, false, updated.version()));
        mvc.perform(get("/api/v1/public/share-bundles/{token}/items/{item}/images/{image}", token, item, image.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/pool/{id}/images/{image}", poolId, image.id()).with(jwt().jwt(j -> j.subject(reader)))).andExpect(status().isNotFound());
    }

    @Test void endedLegacyLinksDoNotConsumeTheRestoredResourcesActiveLinkLimit() {
        var expires = Instant.now().plus(7, ChronoUnit.DAYS);
        for (int i = 0; i < 20; i++) {
            legacyMarkdown.create(owner, document, new MarkdownShareDtos.CreateRequest(expires));
            legacyKnowledge.create(owner, com.springda.devnest.share.KnowledgeResourceType.SNIPPET, snippet,
                    new com.springda.devnest.share.KnowledgeShareDtos.CreateRequest(expires));
        }
        markdown.delete(owner, document); snippets.delete(owner, snippet);
        markdown.restoreFromTrash(owner, document); snippets.restore(owner, snippet);
        assertThat(legacyMarkdown.create(owner, document, new MarkdownShareDtos.CreateRequest(expires)).token()).hasSize(43);
        assertThat(legacyKnowledge.create(owner, com.springda.devnest.share.KnowledgeResourceType.SNIPPET, snippet,
                new com.springda.devnest.share.KnowledgeShareDtos.CreateRequest(expires)).token()).hasSize(43);
    }

    @Test void optionalImageTitlesWorkAcrossPoolBundleAndLegacyWithoutWideningMembership() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");
        var image = images.upload(owner, "caption.png", png);
        var outsider = images.upload(reader, "private.png", png);
        var unused = images.upload(owner, "unused.png", png);
        var current = markdown.get(owner, document);
        markdown.update(owner, document, new MarkdownDocumentDtos.UpdateRequest("Captioned", "note.md", "![alt](" + image.url() + " \"caption\")\n![private](" + outsider.url() + " 'title')", null, false, current.version()));
        publish("[" + ref("MARKDOWN", document) + "]").andExpect(status().isCreated());
        String poolBody = mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(owner)))).andReturn().getResponse().getContentAsString();
        String poolId = JsonPath.read(poolBody, "$.items[0].id");
        String token = JsonPath.read(createLink("[" + ref("MARKDOWN", document) + "]"), "$.token");
        String directory = mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(directory, "$.items[0].id");
        var legacy = legacyMarkdown.create(owner, document, new MarkdownShareDtos.CreateRequest(Instant.now().plusSeconds(86400)));
        org.mockito.Mockito.doAnswer(call -> { ((java.io.OutputStream) call.getArgument(1)).write(png); return null; })
                .when(storage).transferTo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(java.io.OutputStream.class));
        String[] endpoints = {"/api/v1/public/share-bundles/" + token + "/items/" + itemId + "/images/", "/api/v1/sharing/pool/" + poolId + "/images/", "/api/v1/public/markdown-shares/" + legacy.token() + "/images/"};
        for (String endpoint : endpoints) {
            var download = mvc.perform(get(endpoint + image.id()).with(jwt().jwt(j -> j.subject(reader)))).andExpect(request().asyncStarted()).andReturn();
            mvc.perform(asyncDispatch(download)).andExpect(status().isOk()).andExpect(content().bytes(png));
            mvc.perform(get(endpoint + outsider.id()).with(jwt().jwt(j -> j.subject(reader)))).andExpect(status().isNotFound());
            mvc.perform(get(endpoint + unused.id()).with(jwt().jwt(j -> j.subject(reader)))).andExpect(status().isNotFound());
        }
    }

    @Test void administratorCanManageDisabledMembersSharesWithoutPublishingOrReadingTheirAudienceContent() throws Exception {
        publish(mixed()).andExpect(status().isCreated());
        var legacy = legacyMarkdown.create(owner, document, new MarkdownShareDtos.CreateRequest(Instant.now().plusSeconds(86400)));
        var legacySnippet = legacyKnowledge.create(owner, com.springda.devnest.share.KnowledgeResourceType.SNIPPET, snippet,
                new com.springda.devnest.share.KnowledgeShareDtos.CreateRequest(Instant.now().plusSeconds(86400)));
        String created = createLink(mixed()), second = createLink(mixed());
        String linkId = JsonPath.read(created, "$.id"), token = JsonPath.read(created, "$.token"), secondId = JsonPath.read(second, "$.id");
        String poolBody = mvc.perform(get("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(owner)))).andReturn().getResponse().getContentAsString();
        String poolId = JsonPath.read(poolBody, "$.items[0].id");
        var admin = users.saveAndFlush(new UserEntity("admin-sharing@example.test", "hash", "Sharing Admin", UserRole.ADMIN));
        String adminAuth = "Bearer " + authTokens.createAccessToken(admin);
        String readerAuth = "Bearer " + authTokens.createAccessToken(users.findById(reader).orElseThrow());
        String oldOwnerAuth = "Bearer " + authTokens.createAccessToken(users.findById(owner).orElseThrow());
        var user = users.findById(owner).orElseThrow(); user.setEnabled(false); users.saveAndFlush(user);
        mvc.perform(get("/api/v1/sharing/links").header("Authorization", oldOwnerAuth)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sharing/links").header("Authorization", readerAuth).header("X-Workspace-Owner", owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sharing/links/{id}", linkId).header("Authorization", readerAuth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sharing/links").header("Authorization", adminAuth).header("X-Workspace-Owner", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].active").value(false));
        String detail = mvc.perform(get("/api/v1/sharing/links/{id}", linkId).header("Authorization", adminAuth).header("X-Workspace-Owner", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].available").value(false)).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(detail, "$.items[0].id");
        mvc.perform(get("/api/v1/sharing/pool").param("mine", "true").header("Authorization", adminAuth).header("X-Workspace-Owner", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v1/sharing/pool/{id}", poolId).header("Authorization", adminAuth).header("X-Workspace-Owner", owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/share-bundles/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/sharing/pool").header("Authorization", adminAuth).header("X-Workspace-Owner", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":" + mixed() + "}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/sharing/links").header("Authorization", adminAuth).header("X-Workspace-Owner", owner)
                .contentType(MediaType.APPLICATION_JSON).content(bundle(mixed()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/markdown-documents/{id}/shares", document).header("Authorization", adminAuth).header("X-Workspace-Owner", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":\"" + Instant.now().plusSeconds(86400) + "\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/snippets/{id}/shares", snippet).header("Authorization", adminAuth).header("X-Workspace-Owner", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":\"" + Instant.now().plusSeconds(86400) + "\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/markdown-documents/{id}/shares/{share}", document, legacy.id()).header("Authorization", adminAuth).header("X-Workspace-Owner", owner)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/snippets/{id}/shares/{share}", snippet, legacySnippet.id()).header("Authorization", adminAuth).header("X-Workspace-Owner", owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/sharing/pool/revoke").header("Authorization", adminAuth).header("X-Workspace-Owner", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + poolId + "\"]}")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/sharing/links/{id}/items/{item}", linkId, itemId).header("Authorization", adminAuth).header("X-Workspace-Owner", owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/sharing/links/revoke").header("Authorization", adminAuth).header("X-Workspace-Owner", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + linkId + "\",\"" + secondId + "\"]}")).andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions publish(String items) throws Exception {
        return mvc.perform(post("/api/v1/sharing/pool").with(jwt().jwt(j -> j.subject(owner)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":" + items + "}"));
    }
    private String createLink(String items) throws Exception {
        return mvc.perform(post("/api/v1/sharing/links").with(jwt().jwt(j -> j.subject(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(bundle(items))).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
    }
    private String bundle(String items) { return "{\"title\":\"Release collection\",\"items\":" + items + ",\"expiresAt\":\"" + Instant.now().plus(7, ChronoUnit.DAYS) + "\"}"; }
    private String mixed() { return "[" + ref("MARKDOWN", document) + "," + ref("SNIPPET", snippet) + "," + ref("FLOWCHART", log) + "]"; }
    private String ref(String type, String id) { return "{\"type\":\"" + type + "\",\"id\":\"" + id + "\"}"; }

    @Test void flowchartPoolReadsLiveStructuredGraphSearchesLabelsAndInvalidatesOnTrash() throws Exception {
        var d=logs.get(owner,log);
        logs.update(owner,log,new FlowchartDtos.UpdateRequest(d.title(),null,false,com.springda.devnest.flowchart.FlowchartFixtures.graph("pool-node-needle"),d.version(),FlowchartDtos.SaveMode.MANUAL));
        publish(mixed()).andExpect(status().isCreated());
        String list=mvc.perform(get("/api/v1/sharing/pool").param("q","pool-node-needle").with(jwt().jwt(j->j.subject(reader))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].diagram").doesNotExist()).andReturn().getResponse().getContentAsString();
        String entry=JsonPath.read(list,"$.items[0].id");
        mvc.perform(get("/api/v1/sharing/pool/{id}",entry).with(jwt().jwt(j->j.subject(reader))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagram.nodes[0].label").value("pool-node-needle")).andExpect(jsonPath("$.content").value("pool-node-needle\nedge label"));
        d=logs.get(owner,log);logs.update(owner,log,new FlowchartDtos.UpdateRequest(d.title(),null,false,com.springda.devnest.flowchart.FlowchartFixtures.graph("new saved label"),d.version(),FlowchartDtos.SaveMode.MANUAL));
        mvc.perform(get("/api/v1/sharing/pool/{id}",entry).with(jwt().jwt(j->j.subject(reader)))).andExpect(jsonPath("$.diagram.nodes[0].label").value("new saved label"));
        mvc.perform(get("/api/v1/sharing/pool/{id}/images/{image}",entry,"arbitrary").with(jwt().jwt(j->j.subject(reader)))).andExpect(status().isNotFound());
        logs.delete(owner,log);logs.restore(owner,log);
        mvc.perform(get("/api/v1/sharing/pool/{id}",entry).with(jwt().jwt(j->j.subject(reader)))).andExpect(status().isNotFound());
    }
}
