package com.springda.devnest;

import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.markdown.*;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** TEST_POSTGRES_URL must point to a disposable database, never a development/production database. */
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(properties = {
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItcG9zdGdyZXMtaW50ZWdyYXRpb24=",
        "app.cors.allowed-origins=http://localhost:5173"
})
@Transactional
class PostgreSqlIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TEST_POSTGRES_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("TEST_POSTGRES_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_POSTGRES_PASSWORD"));
    }

    @Autowired UserRepository users;
    @Autowired MarkdownDocumentService markdown;
    @Autowired MarkdownDocumentRepository documents;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;

    @Test
    void emptyDatabaseStartsWithBusinessSchemaReadyForManualImport() {
        assertThat(users.count()).isZero();
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class))
                .contains("system_announcements", "announcement_reads", "community_messages", "knowledge_share_links");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'admin_audit_events' AND column_name = 'response_status'", String.class))
                .isEqualTo("integer");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'developer_profiles'", String.class))
                .contains("gender", "avatar_object_key", "avatar_content_type", "avatar_size_bytes");
    }

    @Test
    void normalizedNicknameKeyPreventsConcurrentDuplicates() {
        users.saveAndFlush(new UserEntity("nickname-one@example.test", "hash", "SameName"));
        assertThatThrownBy(() -> users.saveAndFlush(new UserEntity("nickname-two@example.test", "hash", "samename")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void shareTokenRemainsUniqueAcrossResourceTypes() {
        var owner = users.saveAndFlush(new UserEntity("share-schema@example.test", "hash", "Share Schema"));
        String insert = "INSERT INTO knowledge_share_links (id, resource_type, resource_id, owner_id, token_digest, expires_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        jdbc.update(insert, "share-one", "SNIPPET", "snippet", owner.getId(), "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update(insert, "share-two", "DEV_LOG", "log", owner.getId(), "a".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void flywaySchemaValidatesAndPreservesUnicodeHistoryVersionsAndIsolation() {
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class)).isEqualTo("17");
        var owner = users.saveAndFlush(new UserEntity("pg@example.test", "$2a$12$preserved-password-hash", "Postgres 用户"));
        String content = "# 中文知识库 🧠\n代码与引号 ' \\\n".repeat(6000);
        var first = markdown.create(owner.getId(), new MarkdownDocumentDtos.CreateRequest("知识库", "中文.md", content, null, true));
        documents.flush();
        entities.clear();
        assertThat(markdown.get(owner.getId(), first.id()).content()).isEqualTo(content);
        assertThat(documents.sumContentOctetsByOwnerId(owner.getId())).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        assertThat(documents.sumContentOctetsByOwnerIdAndIdIn(owner.getId(), List.of(first.id())))
                .isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        assertThat(markdown.list(owner.getId())).hasSize(1);
        assertThatThrownBy(() -> markdown.get("another-owner", first.id())).isInstanceOf(NotFoundException.class);
        var updated = markdown.update(owner.getId(), first.id(), new MarkdownDocumentDtos.UpdateRequest("知识库", "中文.md", "第二版", null, true, first.version()));
        documents.flush();
        assertThat(updated.version()).isGreaterThan(first.version());
        assertThatThrownBy(() -> markdown.update(owner.getId(), first.id(),
                new MarkdownDocumentDtos.UpdateRequest("知识库", "中文.md", "旧稿", null, true, first.version())))
                .isInstanceOf(ConflictException.class);
        assertThat(markdown.history(owner.getId(), first.id(), 0)).hasSize(2);
        markdown.delete(owner.getId(), first.id());
        documents.flush();
        assertThat(markdown.trash(owner.getId())).hasSize(1);
        assertThat(markdown.restoreFromTrash(owner.getId(), first.id()).content()).isEqualTo("第二版");
    }

    @Test
    void emailUniquenessRemainsCaseInsensitiveAtDatabaseLevel() {
        users.saveAndFlush(new UserEntity("Example@example.test", "hash", "Email One"));
        assertThat(users.findByEmailIgnoreCase("EXAMPLE@example.test")).isPresent();
        assertThatThrownBy(() -> users.saveAndFlush(new UserEntity("example@example.test", "hash", "Email Two")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeysStillCascadeArticleHistoryWhenAnAccountIsDeleted() {
        var owner = users.saveAndFlush(new UserEntity("cascade@example.test", "hash", "Cascade"));
        markdown.create(owner.getId(), new MarkdownDocumentDtos.CreateRequest("Cascade", "cascade.md", "正文", null, false));
        documents.flush();
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM markdown_documents WHERE owner_id = ?", Long.class, owner.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM markdown_document_revisions WHERE owner_id = ?", Long.class, owner.getId())).isZero();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_PGVECTOR", matches = "true")
    void pgvectorSupportsCosineNearestNeighborQueries() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TEMP TABLE vector_smoke (id integer, embedding vector(3)) ON COMMIT DROP");
        jdbc.execute("INSERT INTO vector_smoke VALUES (1, '[1,0,0]'), (2, '[0,1,0]')");
        assertThat(jdbc.queryForObject("SELECT id FROM vector_smoke ORDER BY embedding <=> '[1,0,0]'::vector LIMIT 1", Integer.class)).isEqualTo(1);
    }
}
