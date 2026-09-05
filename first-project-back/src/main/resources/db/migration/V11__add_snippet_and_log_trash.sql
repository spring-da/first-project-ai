ALTER TABLE code_snippets
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD INDEX idx_code_snippets_trash (owner_id, deleted_at);

ALTER TABLE dev_logs
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD INDEX idx_dev_logs_trash (owner_id, deleted_at);
