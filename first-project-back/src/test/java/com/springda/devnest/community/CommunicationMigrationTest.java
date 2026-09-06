package com.springda.devnest.community;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationMigrationTest {

    @Test
    void v14CreatesAnnouncementReadAndThreadedMessageTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:communication-migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id VARCHAR(36) NOT NULL PRIMARY KEY)");

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V14__add_announcements_and_community_messages.sql"));

            var tables = connection.getMetaData().getTables(null, "PUBLIC", null, new String[]{"TABLE"});
            var names = new java.util.HashSet<String>();
            while (tables.next()) names.add(tables.getString("TABLE_NAME"));
            assertThat(names).contains(
                    "SYSTEM_ANNOUNCEMENTS", "ANNOUNCEMENT_READS", "COMMUNITY_MESSAGES");
        }
    }
}
