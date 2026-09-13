package com.springda.devnest.flowchart;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.springda.devnest.user.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:flowcharts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.flyway.enabled=false",
            "app.oss.enabled=false",
            "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3Ita25vd2xlZGdlLXNoYXJlLXRlc3Rz",
            "app.account-security.bootstrap-admin-required=false",
            "app.cors.allowed-origins=http://localhost:5173"
        })
@Transactional
class FlowchartIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    MockMvc mvc;
    String owner;
    String other;
    static final String EMPTY = "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = users.saveAndFlush(new UserEntity("flow@example.test", "hash", "Flow")).getId();
        other = users.saveAndFlush(new UserEntity("other@example.test", "hash", "Other")).getId();
    }

    String draft(String diagram) {
        return "{\"title\":\" Diagram \",\"domainId\":null,\"favorite\":false,\"diagram\":"
                + diagram;
    }

    String create() throws Exception {
        return mvc.perform(
                        post("/api/v1/flowcharts")
                                .with(jwt().jwt(j -> j.subject(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        draft(EMPTY)
                                                + ",\"creationKey\":\"11111111-1111-4111-8111-111111111111\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Diagram"))
                .andExpect(jsonPath("$.diagram.schemaVersion").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void authenticatedCreationIsIdempotentAndSummariesOmitBody() throws Exception {
        mvc.perform(get("/api/v1/flowcharts")).andExpect(status().isUnauthorized());
        String created = create();
        String id = JsonPath.read(created, "$.id");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(create(), "$.id"))
                .isEqualTo(id);
        mvc.perform(get("/api/v1/flowcharts").with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].diagram").doesNotExist());
        mvc.perform(get("/api/v1/flowcharts/{id}", id).with(jwt().jwt(j -> j.subject(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void titleLengthIsCheckedAfterTrimmingOnCreateAndUpdate() throws Exception {
        var title = "x".repeat(200);
        var request =
                json.createObjectNode()
                        .put("title", " \t" + title + "\n ")
                        .putNull("domainId")
                        .put("favorite", false)
                        .put("creationKey", java.util.UUID.randomUUID().toString());
        request.set("diagram", json.readTree(EMPTY));
        String body =
                mvc.perform(
                                post("/api/v1/flowcharts")
                                        .with(jwt().jwt(j -> j.subject(owner)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.title").value(title))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = JsonPath.read(body, "$.id");
        var updated = "y".repeat(200);
        var update = request.deepCopy().put("title", " " + updated + " ").put("expectedVersion", 0);
        update.remove("creationKey");
        mvc.perform(
                        put("/api/v1/flowcharts/{id}", id)
                                .with(jwt().jwt(j -> j.subject(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(updated));
        for (String invalid : new String[] {" \t\n ", " " + "z".repeat(201) + " "}) {
            request.put("title", invalid)
                    .put("creationKey", java.util.UUID.randomUUID().toString());
            update.put("title", invalid).put("expectedVersion", 1);
            mvc.perform(
                            post("/api/v1/flowcharts")
                                    .with(jwt().jwt(j -> j.subject(owner)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
            mvc.perform(
                            put("/api/v1/flowcharts/{id}", id)
                                    .with(jwt().jwt(j -> j.subject(owner)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json.writeValueAsString(update)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void staleSavesConflictAndTrashCanOnlyBePurgedAfterDeletion() throws Exception {
        String id = JsonPath.read(create(), "$.id");
        mvc.perform(
                        put("/api/v1/flowcharts/{id}", id)
                                .with(jwt().jwt(j -> j.subject(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        draft(EMPTY).replace(" Diagram ", "Changed")
                                                + ",\"expectedVersion\":0,\"saveMode\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(
                        put("/api/v1/flowcharts/{id}", id)
                                .with(jwt().jwt(j -> j.subject(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(draft(EMPTY) + ",\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        delete("/api/v1/flowcharts/{id}/permanent", id)
                                .with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/flowcharts/{id}", id).with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/flowcharts/{id}", id).with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/flowcharts/{id}/restore", id)
                                .with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isOk());
    }

    @Test
    void graphUnknownFieldsAreRejected() throws Exception {
        mvc.perform(
                        post("/api/v1/flowcharts")
                                .with(jwt().jwt(j -> j.subject(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        draft(
                                                        EMPTY.replace(
                                                                "\"edges\":[]",
                                                                "\"edges\":[],\"html\":\"remote\""))
                                                + ",\"creationKey\":\"11111111-1111-4111-8111-111111111111\"}"))
                .andExpect(status().isBadRequest());
    }

    @Autowired FlowchartService flowcharts;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired com.springda.devnest.auth.TokenService tokens;

    @Test
    void signedJwtAndAdminWorkspaceRemainOwnerScoped() throws Exception {
        String id = JsonPath.read(create(), "$.id");
        String token = tokens.createAccessToken(users.findById(owner).orElseThrow());
        mvc.perform(get("/api/v1/flowcharts/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/flowcharts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Workspace-Owner", other))
                .andExpect(status().isForbidden());
        for (String suffix : new String[] {"", "/revisions", "/revisions/missing"})
            mvc.perform(
                            get("/api/v1/flowcharts/" + id + suffix)
                                    .with(jwt().jwt(j -> j.subject(other))))
                    .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/flowcharts/{id}", id).with(jwt().jwt(j -> j.subject(other))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/logs").with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchesNodeAndEdgeLabelsAndTreatsWildcardsLiterally() throws Exception {
        flowcharts.create(
                owner,
                new FlowchartDtos.CreateRequest(
                        "Search",
                        null,
                        false,
                        FlowchartFixtures.graph("needle 100%_ literal"),
                        java.util.UUID.randomUUID()));
        for (String q : new String[] {"NEEDLE", "edge label", "%_"})
            mvc.perform(
                            get("/api/v1/flowcharts")
                                    .param("q", q)
                                    .with(jwt().jwt(j -> j.subject(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nodeCount").value(1))
                    .andExpect(jsonPath("$[0].diagram").doesNotExist());
        mvc.perform(
                        get("/api/v1/flowcharts")
                                .param("q", "absent")
                                .with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void autosaveCheckpointsAreThrottledManualSaveDeduplicatesAndHistoryRetainsLatest100()
            throws Exception {
        var d =
                flowcharts.create(
                        owner,
                        new FlowchartDtos.CreateRequest(
                                "History",
                                null,
                                false,
                                json.readTree(EMPTY),
                                java.util.UUID.randomUUID()));
        d =
                flowcharts.update(
                        owner,
                        d.id(),
                        new FlowchartDtos.UpdateRequest(
                                "Auto 1",
                                null,
                                false,
                                d.diagram(),
                                d.version(),
                                FlowchartDtos.SaveMode.AUTO));
        org.assertj.core.api.Assertions.assertThat(flowcharts.history(owner, d.id(), 0)).hasSize(1);
        d =
                flowcharts.update(
                        owner,
                        d.id(),
                        new FlowchartDtos.UpdateRequest(
                                "Auto 1",
                                null,
                                false,
                                d.diagram(),
                                d.version(),
                                FlowchartDtos.SaveMode.MANUAL));
        org.assertj.core.api.Assertions.assertThat(flowcharts.history(owner, d.id(), 0)).hasSize(2);
        var same =
                flowcharts.update(
                        owner,
                        d.id(),
                        new FlowchartDtos.UpdateRequest(
                                d.title(),
                                null,
                                false,
                                d.diagram(),
                                d.version(),
                                FlowchartDtos.SaveMode.MANUAL));
        org.assertj.core.api.Assertions.assertThat(same.version()).isEqualTo(d.version());
        org.assertj.core.api.Assertions.assertThat(flowcharts.history(owner, d.id(), 0)).hasSize(2);
        jdbc.update(
                "UPDATE flowchart_revisions SET created_at=? WHERE document_id=?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(301)),
                d.id());
        // JDBC timestamp change is not in the persistence context, so clear before testing elapsed
        // time.
        context.getBean(jakarta.persistence.EntityManager.class).clear();
        d =
                flowcharts.update(
                        owner,
                        d.id(),
                        new FlowchartDtos.UpdateRequest(
                                "Auto 2",
                                null,
                                false,
                                d.diagram(),
                                d.version(),
                                FlowchartDtos.SaveMode.AUTO));
        org.assertj.core.api.Assertions.assertThat(flowcharts.history(owner, d.id(), 0)).hasSize(3);
        for (int i = 0; i < 105; i++)
            d =
                    flowcharts.update(
                            owner,
                            d.id(),
                            new FlowchartDtos.UpdateRequest(
                                    "Manual " + i,
                                    null,
                                    false,
                                    d.diagram(),
                                    d.version(),
                                    FlowchartDtos.SaveMode.MANUAL));
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM flowchart_revisions WHERE document_id=?",
                                Long.class,
                                d.id()))
                .isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(flowcharts.history(owner, d.id(), 4))
                .hasSize(20);
        org.assertj.core.api.Assertions.assertThat(flowcharts.history(owner, d.id(), 5)).isEmpty();
        var revision = flowcharts.history(owner, d.id(), 1).getFirst();
        var restored =
                flowcharts.restoreRevision(
                        owner,
                        d.id(),
                        revision.id(),
                        new FlowchartDtos.RestoreRequest(d.version()));
        org.assertj.core.api.Assertions.assertThat(restored.title()).isEqualTo(revision.title());
        org.assertj.core.api.Assertions.assertThat(restored.version()).isGreaterThan(d.version());
        mvc.perform(
                        get("/api/v1/flowcharts/{id}/revisions", d.id())
                                .param("page", "-1")
                                .with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creationRetryInTrashConflictsAndPurgingRemovesHistory() throws Exception {
        String id = JsonPath.read(create(), "$.id");
        flowcharts.delete(owner, id);
        mvc.perform(
                        post("/api/v1/flowcharts")
                                .with(jwt().jwt(j -> j.subject(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        draft(EMPTY)
                                                + ",\"creationKey\":\"11111111-1111-4111-8111-111111111111\"}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        delete("/api/v1/flowcharts/{id}/permanent", id)
                                .with(jwt().jwt(j -> j.subject(owner))))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM flowchart_revisions WHERE document_id=?",
                                Long.class,
                                id))
                .isZero();
    }
}
