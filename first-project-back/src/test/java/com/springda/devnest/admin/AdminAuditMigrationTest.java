package com.springda.devnest.admin;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditMigrationTest {

    @Test
    void responseStatusMigrationMatchesTheJpaIntegerMapping() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:admin-audit-migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE admin_audit_events (
                            response_status SMALLINT NOT NULL
                        )
                        """);
            }

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V13__align_admin_audit_response_status.sql"));

            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(
                    null, "PUBLIC", "ADMIN_AUDIT_EVENTS", "RESPONSE_STATUS")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt("DATA_TYPE")).isEqualTo(Types.INTEGER);
            }
        }

        assertThat(AdminAuditEventEntity.class.getDeclaredField("responseStatus").getType())
                .isEqualTo(int.class);
    }
}
