package com.springda.devnest.flowchart;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Upgrades a synthetic V18 database and restores its pre-upgrade template snapshot.
 * TEST_POSTGRES_URL must identify a disposable test cluster; the account needs CREATEDB. This
 * in-cluster physical-clone rehearsal does not substitute for an off-host pg_dump backup. No
 * migration, seed, restore, or cleanup SQL targets the configured test database itself.
 */
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class FlowchartMigrationPostgreSqlTest {
    private static final Set<String> CHANGED_TABLES =
            Set.of(
                    "dev_logs",
                    "dev_log_tags",
                    "knowledge_share_links",
                    "sharing_pool_entries",
                    "share_bundles",
                    "share_bundle_items",
                    "flyway_schema_history");

    @Test
    void upgradesLegacySharingAndRestoresVerifiedPreMigrationSnapshot() throws Exception {
        String configuredUrl = System.getenv("TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("TEST_POSTGRES_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "");
        var coordinator = dataSource(configuredUrl, user, password);
        var control = new JdbcTemplate(coordinator);
        var created = new ArrayList<String>();
        String prefix = "flowchart_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String source = prefix + "_src", backup = prefix + "_bak", restored = prefix + "_restore";
        try {
            createDatabase(control, created, source, null);
            String sourceUrl = databaseUrl(configuredUrl, source);
            var sourceDataSource = dataSource(sourceUrl, user, password);
            migrations(sourceDataSource, "18").migrate();
            try (var connection = sourceDataSource.getConnection()) {
                ScriptUtils.executeSqlScript(
                        connection,
                        new EncodedResource(
                                new ClassPathResource("flowchart-upgrade-v18-fixture.sql"),
                                StandardCharsets.UTF_8));
            }
            var original = new JdbcTemplate(sourceDataSource);
            assertThat(version(original)).isEqualTo("18");
            assertThat(count(original, "dev_logs", "deleted_at IS NULL")).isEqualTo(1);
            assertThat(count(original, "dev_logs", "deleted_at IS NOT NULL")).isEqualTo(1);
            assertThat(count(original, "dev_log_tags", "TRUE")).isEqualTo(2);
            assertThat(count(original, "knowledge_share_links", "resource_type='DEV_LOG'"))
                    .isEqualTo(2);
            assertThat(count(original, "sharing_pool_entries", "resource_type='DEV_LOG'"))
                    .isEqualTo(2);
            assertThat(count(original, "share_bundle_items", "resource_type='DEV_LOG'"))
                    .isEqualTo(5);
            var before = snapshot(original);
            var retainedLegacy = rows(original, "knowledge_share_links", "resource_type='SNIPPET'");
            var retainedPool = rows(original, "sharing_pool_entries", "resource_type<>'DEV_LOG'");
            var retainedItems = rows(original, "share_bundle_items", "resource_type<>'DEV_LOG'");
            var mixedBundle = rows(original, "share_bundles", "id='bundle-mixed'");

            // All connections are closed before PostgreSQL takes a physical template copy.
            createDatabase(control, created, backup, source);
            var backupDataSource = dataSource(databaseUrl(configuredUrl, backup), user, password);
            assertThat(snapshot(new JdbcTemplate(backupDataSource))).isEqualTo(before);
            migrations(backupDataSource, "18").validate();

            var upgrade = migrations(sourceDataSource, "19").migrate();
            assertThat(upgrade.migrationsExecuted).isEqualTo(1);
            assertThat(version(original)).isEqualTo("19");
            var after = snapshot(original);
            assertThat(after).doesNotContainKeys("dev_logs", "dev_log_tags");
            assertThat(after.get("flowcharts")).isEmpty();
            assertThat(after.get("flowchart_revisions")).isEmpty();
            // Includes article body/history/share, snippet body, owner, folders and old audit
            // events.
            before.forEach(
                    (table, contents) -> {
                        if (!CHANGED_TABLES.contains(table))
                            assertThat(after.get(table)).as(table).isEqualTo(contents);
                    });
            assertThat(rows(original, "knowledge_share_links", "TRUE")).isEqualTo(retainedLegacy);
            assertThat(rows(original, "sharing_pool_entries", "TRUE")).isEqualTo(retainedPool);
            assertThat(rows(original, "share_bundle_items", "TRUE")).isEqualTo(retainedItems);
            assertThat(rows(original, "share_bundles", "id='bundle-mixed'")).isEqualTo(mixedBundle);
            assertThat(
                            count(
                                    original,
                                    "share_bundles",
                                    "id='bundle-logs' AND revoked_at IS NOT NULL"))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    original,
                                    "share_bundles",
                                    "id='bundle-revoked' AND revoked_at='2025-01-01T00:00:00Z'"))
                    .isEqualTo(1);
            assertThat(count(original, "share_bundles", "TRUE")).isEqualTo(3);
            assertThat(
                            count(
                                    original,
                                    "knowledge_share_links",
                                    "token_digest IN (repeat('c',64),repeat('d',64))"))
                    .isZero();
            assertThat(
                            original.queryForObject(
                                    "SELECT token_digest FROM share_bundles WHERE id='bundle-logs'",
                                    String.class))
                    .isEqualTo("f".repeat(64));
            assertThat(
                            original.queryForObject(
                                            "SELECT revoked_at FROM share_bundles WHERE"
                                                + " id='bundle-logs'",
                                            java.sql.Timestamp.class)
                                    .toInstant())
                    .isBeforeOrEqualTo(Instant.now());

            // Rehearse rollback after the destructive migration using only the verified backup.
            createDatabase(control, created, restored, backup);
            var restoreDataSource =
                    dataSource(databaseUrl(configuredUrl, restored), user, password);
            var recovery = new JdbcTemplate(restoreDataSource);
            assertThat(version(recovery)).isEqualTo("18");
            assertThat(snapshot(recovery)).isEqualTo(before);
            migrations(restoreDataSource, "18").validate();
            // The restored database can independently follow the same upgrade path.
            migrations(restoreDataSource, "19").migrate();
            assertThat(count(recovery, "share_bundle_items", "TRUE")).isEqualTo(2);
            assertThat(count(recovery, "share_bundles", "revoked_at IS NOT NULL")).isEqualTo(2);
            assertThat(rows(recovery, "markdown_documents", "TRUE"))
                    .isEqualTo(before.get("markdown_documents"));
            assertThat(rows(recovery, "code_snippets", "TRUE"))
                    .isEqualTo(before.get("code_snippets"));
        } finally {
            // Only names successfully created by this invocation are eligible for cleanup.
            for (String database : created.reversed()) {
                requireGeneratedName(database);
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }

    private static DriverManagerDataSource dataSource(String url, String user, String password) {
        return new DriverManagerDataSource(url, user, password);
    }

    private static Flyway migrations(DriverManagerDataSource database, String target) {
        return Flyway.configure()
                .dataSource(database)
                .locations("classpath:db/postgresql")
                .target(target)
                .load();
    }

    private static void createDatabase(
            JdbcTemplate control, List<String> created, String name, String template) {
        requireGeneratedName(name);
        if (template != null) {
            requireGeneratedName(template);
            assertThat(created).contains(template);
        }
        control.execute(
                "CREATE DATABASE \""
                        + name
                        + "\""
                        + (template == null
                                ? " TEMPLATE template0"
                                : " TEMPLATE \"" + template + "\""));
        created.add(name);
    }

    private static void requireGeneratedName(String name) {
        if (!name.matches("flowchart_upgrade_[a-f0-9]{32}_(src|bak|restore)"))
            throw new IllegalArgumentException("Refusing to modify a non-generated database");
    }

    private static String databaseUrl(String configured, String name) throws Exception {
        requireGeneratedName(name);
        if (!configured.startsWith("jdbc:postgresql://"))
            throw new IllegalArgumentException(
                    "TEST_POSTGRES_URL must be a PostgreSQL network JDBC URL");
        var uri = new URI(configured.substring("jdbc:".length()));
        return "jdbc:"
                + new URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        uri.getHost(),
                        uri.getPort(),
                        "/" + name,
                        uri.getQuery(),
                        null);
    }

    private static String version(JdbcTemplate database) {
        return database.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"
                    + " DESC LIMIT 1",
                String.class);
    }

    private static long count(JdbcTemplate database, String table, String condition) {
        return database.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + condition, Long.class);
    }

    private static Map<String, List<String>> snapshot(JdbcTemplate database) {
        var snapshot = new LinkedHashMap<String, List<String>>();
        for (String table :
                database.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY"
                            + " tablename",
                        String.class)) {
            if (!table.matches("[a-z_]+"))
                throw new IllegalArgumentException("Unexpected fixture table");
            snapshot.put(table, rows(database, table, "TRUE"));
        }
        return snapshot;
    }

    private static List<String> rows(JdbcTemplate database, String table, String condition) {
        return database.queryForList(
                "SELECT row_to_json(t)::text FROM "
                        + table
                        + " t WHERE "
                        + condition
                        + " ORDER BY row_to_json(t)::text",
                String.class);
    }
}
