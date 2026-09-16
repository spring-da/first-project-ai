package com.springda.devnest.sharing;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** All filtering, counts and pagination run in the database; bodies stay out of list responses. */
@Repository
public class SharingQueries {
    private static final String RESOURCES = """
            WITH resources AS (
                SELECT id, owner_id, 'MARKDOWN' AS resource_type, title, content, updated_at, sharing_generation
                FROM markdown_documents WHERE deleted_at IS NULL
                UNION ALL
                SELECT id, owner_id, 'SNIPPET', title, code, updated_at, sharing_generation
                FROM code_snippets WHERE deleted_at IS NULL
                UNION ALL
                SELECT id, owner_id, 'FLOWCHART', title, search_text, updated_at, sharing_generation
                FROM flowcharts WHERE deleted_at IS NULL
            )
            """;
    private static final String POOL_FROM = """
            FROM sharing_pool_entries p JOIN users u ON u.id = p.owner_id
            JOIN resources r ON r.id = p.resource_id AND r.owner_id = p.owner_id
                AND r.resource_type = p.resource_type AND r.sharing_generation = p.resource_generation
            WHERE p.revoked_at IS NULL
            """;
    private static final String ITEM_JOIN = """
            LEFT JOIN resources r ON r.id = i.resource_id AND r.owner_id = :owner
                AND r.resource_type = i.resource_type AND r.sharing_generation = i.resource_generation
            """;
    private static final String LIVE_COUNT = """
            (SELECT COUNT(*) FROM share_bundle_items i JOIN resources r
                ON r.id = i.resource_id AND r.owner_id = b.owner_id AND r.resource_type = i.resource_type
                AND r.sharing_generation = i.resource_generation
                WHERE i.bundle_id = b.id AND i.removed_at IS NULL)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entities;
    public SharingQueries(NamedParameterJdbcTemplate jdbc, EntityManager entities) { this.jdbc = jdbc; this.entities = entities; }
    public SharingDtos.Page<SharingDtos.PoolSummary> pool(String owner, int page, int size, String q, ResourceType type, boolean mine) {
        // JDBC shares the JPA transaction; flush pending resource changes before evaluating visibility.
        entities.flush();
        var params = params(owner, page, size, q);
        String where = POOL_FROM;
        if (mine) where += " AND p.owner_id = :owner";
        else where += " AND u.enabled = TRUE";
        if (type != null) { where += " AND p.resource_type = :type"; params.addValue("type", type.name()); }
        if (!q.isBlank()) where += " AND (LOWER(r.title) LIKE :q ESCAPE '!' OR LOWER(r.content) LIKE :q ESCAPE '!' OR LOWER(u.display_name) LIKE :q ESCAPE '!')";
        long total = jdbc.queryForObject(RESOURCES + "SELECT COUNT(*) " + where, params, Long.class);
        var items = jdbc.query(RESOURCES + "SELECT p.id, p.owner_id, p.resource_type, r.title, SUBSTRING(r.content, 1, 240) AS excerpt, u.display_name, p.shared_at, r.updated_at "
                + where + " ORDER BY p.shared_at DESC, p.id DESC LIMIT :size OFFSET :offset", params,
                (r, row) -> new SharingDtos.PoolSummary(r.getString("id"), ResourceType.valueOf(r.getString("resource_type")),
                        r.getString("title"), r.getString("excerpt"), r.getString("display_name"), instant(r, "shared_at"), instant(r, "updated_at"), owner.equals(r.getString("owner_id"))));
        return new SharingDtos.Page<>(items, total, page, size);
    }
    public SharingDtos.Page<SharingDtos.LinkSummary> links(String owner, int page, int size, String q) {
        entities.flush();
        var params = params(owner, page, size, q);
        String where = " FROM share_bundles b JOIN users u ON u.id = b.owner_id WHERE b.owner_id = :owner";
        if (!q.isBlank()) where += " AND LOWER(b.title) LIKE :q ESCAPE '!'";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, params, Long.class);
        var items = jdbc.query(RESOURCES + "SELECT b.*, u.enabled, " + LIVE_COUNT + " AS item_count" + where
                + " ORDER BY b.created_at DESC, b.id DESC LIMIT :size OFFSET :offset", params, (r, row) -> summary(r));
        return new SharingDtos.Page<>(items, total, page, size);
    }
    public SharingDtos.LinkSummary link(String owner, String id) {
        entities.flush();
        return jdbc.query(RESOURCES + "SELECT b.*, u.enabled, " + LIVE_COUNT + " AS item_count FROM share_bundles b JOIN users u ON u.id = b.owner_id WHERE b.owner_id = :owner AND b.id = :id",
                new MapSqlParameterSource("owner", owner).addValue("id", id), (r, row) -> summary(r)).stream().findFirst().orElseThrow(SharingResources::unavailable);
    }
    public List<SharingDtos.ManagedItem> items(String owner, String bundleId, boolean enabled) {
        entities.flush();
        return jdbc.query(RESOURCES + "SELECT i.*, COALESCE(r.title, i.original_title) AS title, r.id AS live_id FROM share_bundle_items i "
                + ITEM_JOIN + " WHERE i.bundle_id = :id ORDER BY i.sort_order, i.id", new MapSqlParameterSource("owner", owner).addValue("id", bundleId),
                (r, row) -> new SharingDtos.ManagedItem(r.getString("id"), ResourceType.valueOf(r.getString("resource_type")), r.getString("resource_id"),
                        r.getString("title"), instant(r, "removed_at"), enabled && r.getString("live_id") != null && r.getTimestamp("removed_at") == null));
    }
    private SharingDtos.LinkSummary summary(ResultSet r) throws SQLException {
        var expires = instant(r, "expires_at"); var revoked = instant(r, "revoked_at"); int count = r.getInt("item_count");
        return new SharingDtos.LinkSummary(r.getString("id"), r.getString("title"), expires, instant(r, "created_at"), revoked,
                r.getBoolean("enabled") && revoked == null && expires.isAfter(Instant.now()) && count > 0, count);
    }
    private MapSqlParameterSource params(String owner, int page, int size, String q) {
        String search = q.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return new MapSqlParameterSource("owner", owner).addValue("size", size).addValue("offset", (long) page * size).addValue("q", "%" + search + "%");
    }
    private Instant instant(ResultSet result, String column) throws SQLException {
        var value = result.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
