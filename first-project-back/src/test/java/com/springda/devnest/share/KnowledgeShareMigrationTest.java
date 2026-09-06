package com.springda.devnest.share;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeShareMigrationTest {

    @Test
    void v16AddsShareLinksForSnippetsAndDevLogs() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:knowledge-share-migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id VARCHAR(36) NOT NULL PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V16__add_knowledge_share_links.sql"));

            var columns = connection.getMetaData().getColumns(
                    null, "PUBLIC", "KNOWLEDGE_SHARE_LINKS", null);
            var names = new java.util.HashSet<String>();
            while (columns.next()) names.add(columns.getString("COLUMN_NAME"));
            assertThat(names).contains(
                    "RESOURCE_TYPE", "RESOURCE_ID", "OWNER_ID", "TOKEN_DIGEST", "EXPIRES_AT", "REVOKED_AT");

            statement.execute("INSERT INTO users (id) VALUES ('owner')");
            statement.execute("INSERT INTO knowledge_share_links "
                    + "(id, resource_type, resource_id, owner_id, token_digest, expires_at) VALUES "
                    + "('one', 'SNIPPET', 'snippet', 'owner', 'digest', CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> statement.execute("INSERT INTO knowledge_share_links "
                    + "(id, resource_type, resource_id, owner_id, token_digest, expires_at) VALUES "
                    + "('two', 'DEV_LOG', 'log', 'owner', 'digest', CURRENT_TIMESTAMP)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
