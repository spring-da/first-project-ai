ALTER TABLE users
    ADD COLUMN display_name_key VARCHAR(80) NULL;

CREATE UNIQUE INDEX uk_users_display_name_key
    ON users (display_name_key);

ALTER TABLE developer_profiles
    ADD COLUMN gender VARCHAR(16) NULL;

ALTER TABLE developer_profiles
    ADD COLUMN avatar_object_key VARCHAR(512) NULL;

ALTER TABLE developer_profiles
    ADD COLUMN avatar_content_type VARCHAR(32) NULL;

ALTER TABLE developer_profiles
    ADD COLUMN avatar_size_bytes BIGINT NULL;
