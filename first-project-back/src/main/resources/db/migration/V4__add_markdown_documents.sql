CREATE TABLE markdown_documents (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    domain_id       CHAR(36)       NULL,
    title           VARCHAR(200)   NOT NULL,
    file_name       VARCHAR(255)   NOT NULL,
    content         LONGTEXT       NOT NULL,
    is_favorite     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_markdown_documents PRIMARY KEY (id),
    CONSTRAINT fk_markdown_documents_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_markdown_documents_domain
        FOREIGN KEY (domain_id) REFERENCES knowledge_domains (id) ON DELETE SET NULL,
    INDEX idx_markdown_documents_owner_updated (owner_id, is_favorite, updated_at),
    INDEX idx_markdown_documents_owner_domain_updated (owner_id, domain_id, updated_at),
    FULLTEXT INDEX ft_markdown_documents_search (title, content)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
