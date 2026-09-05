CREATE TABLE markdown_images (
    id           CHAR(36)     NOT NULL,
    owner_id     CHAR(36)     NOT NULL,
    object_key   VARCHAR(512) NOT NULL,
    content_type VARCHAR(32)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_markdown_images PRIMARY KEY (id),
    CONSTRAINT uk_markdown_images_object UNIQUE (object_key),
    CONSTRAINT fk_markdown_images_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_markdown_images_owner (owner_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
