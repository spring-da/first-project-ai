package com.springda.devnest.profile;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileIdentityMigrationTest {

    @Test
    void v15AddsGenderUploadedAvatarMetadataAndNicknameRaceProtection() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:profile-identity-migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id VARCHAR(36) NOT NULL PRIMARY KEY)");
            statement.execute("CREATE TABLE developer_profiles (id VARCHAR(36) NOT NULL PRIMARY KEY)");

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V15__add_profile_identity_fields.sql"));

            var userColumns = connection.getMetaData().getColumns(
                    null, "PUBLIC", "USERS", "DISPLAY_NAME_KEY");
            assertThat(userColumns.next()).isTrue();
            var profileColumns = connection.getMetaData().getColumns(
                    null, "PUBLIC", "DEVELOPER_PROFILES", null);
            var names = new java.util.HashSet<String>();
            while (profileColumns.next()) names.add(profileColumns.getString("COLUMN_NAME"));
            assertThat(names).contains("GENDER", "AVATAR_OBJECT_KEY", "AVATAR_CONTENT_TYPE", "AVATAR_SIZE_BYTES");

            statement.execute("INSERT INTO users (id, display_name_key) VALUES ('one', 'member')");
            assertThatThrownBy(() -> statement.execute(
                    "INSERT INTO users (id, display_name_key) VALUES ('two', 'member')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
