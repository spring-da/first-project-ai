CREATE TABLE knowledge_domains (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    description     VARCHAR(240)   NOT NULL DEFAULT '',
    sort_order      INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_knowledge_domains PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_domains_owner_name UNIQUE (owner_id, name),
    CONSTRAINT fk_knowledge_domains_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_knowledge_domains_owner_sort (owner_id, sort_order, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE code_snippets
    ADD COLUMN domain_id CHAR(36) NULL AFTER owner_id,
    ADD CONSTRAINT fk_code_snippets_domain
        FOREIGN KEY (domain_id) REFERENCES knowledge_domains (id) ON DELETE SET NULL,
    ADD INDEX idx_code_snippets_owner_domain_updated (owner_id, domain_id, updated_at);

ALTER TABLE dev_logs
    ADD COLUMN domain_id CHAR(36) NULL AFTER owner_id,
    ADD CONSTRAINT fk_dev_logs_domain
        FOREIGN KEY (domain_id) REFERENCES knowledge_domains (id) ON DELETE SET NULL,
    ADD INDEX idx_dev_logs_owner_domain_updated (owner_id, domain_id, updated_at);
