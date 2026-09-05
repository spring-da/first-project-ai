CREATE TABLE markdown_share_links (
    id           CHAR(36)     NOT NULL,
    document_id  CHAR(36)     NOT NULL,
    owner_id     CHAR(36)     NOT NULL,
    token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at   TIMESTAMP(6) NOT NULL,
    revoked_at   TIMESTAMP(6) NULL,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_markdown_share_links PRIMARY KEY (id),
    CONSTRAINT uk_markdown_share_links_token UNIQUE (token_digest),
    CONSTRAINT fk_markdown_share_links_document FOREIGN KEY (document_id)
        REFERENCES markdown_documents (id) ON DELETE CASCADE,
    INDEX idx_markdown_share_links_owner_document (owner_id, document_id, created_at),
    INDEX idx_markdown_share_links_expiry (expires_at, revoked_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
