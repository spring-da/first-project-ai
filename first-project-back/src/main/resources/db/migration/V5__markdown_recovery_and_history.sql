ALTER TABLE markdown_documents
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD INDEX idx_markdown_documents_trash (owner_id, deleted_at);

CREATE TABLE markdown_document_revisions (
    id CHAR(36) NOT NULL,
    document_id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    document_version BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    domain_id CHAR(36) NULL,
    is_favorite BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_markdown_revision_document FOREIGN KEY (document_id)
        REFERENCES markdown_documents (id) ON DELETE CASCADE,
    UNIQUE KEY uk_markdown_revision_version (document_id, document_version),
    INDEX idx_markdown_revision_owner_document (owner_id, document_id, document_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Preserve the current content of every existing article as its initial snapshot.
INSERT INTO markdown_document_revisions
    (id, document_id, owner_id, document_version, action, title, file_name, content,
     domain_id, is_favorite, created_at, updated_at)
SELECT UUID(), id, owner_id, version, 'BASELINE', title, file_name, content,
       domain_id, is_favorite, updated_at, updated_at
FROM markdown_documents;
