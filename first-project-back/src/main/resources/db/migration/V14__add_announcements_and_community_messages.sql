CREATE TABLE system_announcements (
    id            VARCHAR(36)  NOT NULL,
    published_by  VARCHAR(36)  NOT NULL,
    title         VARCHAR(160) NOT NULL,
    content       TEXT         NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_system_announcements PRIMARY KEY (id),
    CONSTRAINT fk_system_announcements_publisher FOREIGN KEY (published_by) REFERENCES users (id),
    INDEX idx_system_announcements_active_created (active, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE announcement_reads (
    id               VARCHAR(36)  NOT NULL,
    announcement_id  VARCHAR(36)  NOT NULL,
    reader_id        VARCHAR(36)  NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_announcement_reads PRIMARY KEY (id),
    CONSTRAINT uk_announcement_reads_reader UNIQUE (announcement_id, reader_id),
    CONSTRAINT fk_announcement_reads_announcement FOREIGN KEY (announcement_id)
        REFERENCES system_announcements (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_reads_reader FOREIGN KEY (reader_id)
        REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_announcement_reads_reader (reader_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE community_messages (
    id                  VARCHAR(36)  NOT NULL,
    parent_id           VARCHAR(36)  NULL,
    author_id           VARCHAR(36)  NOT NULL,
    content             TEXT         NOT NULL,
    image_object_key    VARCHAR(512) NULL,
    image_content_type  VARCHAR(32)  NULL,
    image_size_bytes    BIGINT       NULL,
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_community_messages PRIMARY KEY (id),
    CONSTRAINT fk_community_messages_parent FOREIGN KEY (parent_id)
        REFERENCES community_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_messages_author FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_community_messages_body CHECK (
        CHAR_LENGTH(TRIM(content)) > 0 OR image_object_key IS NOT NULL
    ),
    INDEX idx_community_messages_feed (parent_id, created_at, id),
    INDEX idx_community_messages_author (author_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
