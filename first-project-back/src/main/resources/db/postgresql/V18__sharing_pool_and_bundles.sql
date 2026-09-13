-- Generations change on trash, so restoring content never revives an earlier share.
ALTER TABLE markdown_documents ADD COLUMN sharing_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE code_snippets ADD COLUMN sharing_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE dev_logs ADD COLUMN sharing_generation BIGINT NOT NULL DEFAULT 0;
-- Optimistic locking rejects stale trash/update requests before they can lower a generation.
ALTER TABLE code_snippets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE dev_logs ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE markdown_share_links ADD COLUMN resource_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_share_links ADD COLUMN resource_generation BIGINT NOT NULL DEFAULT 0;

-- Existing links for already trashed content must remain unavailable after restore.
UPDATE markdown_documents SET sharing_generation = 1 WHERE deleted_at IS NOT NULL;
UPDATE code_snippets SET sharing_generation = 1 WHERE deleted_at IS NOT NULL;
UPDATE dev_logs SET sharing_generation = 1 WHERE deleted_at IS NOT NULL;

CREATE TABLE sharing_pool_entries (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resource_type VARCHAR(20) NOT NULL CHECK (resource_type IN ('MARKDOWN', 'SNIPPET', 'DEV_LOG')),
    resource_id VARCHAR(36) NOT NULL,
    resource_generation BIGINT NOT NULL,
    shared_at TIMESTAMPTZ(6) NOT NULL,
    revoked_at TIMESTAMPTZ(6),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sharing_pool_resource UNIQUE(owner_id, resource_type, resource_id)
);
CREATE INDEX idx_sharing_pool_active_order ON sharing_pool_entries(shared_at DESC, id DESC) WHERE revoked_at IS NULL;
CREATE INDEX idx_sharing_pool_owner_order ON sharing_pool_entries(owner_id, shared_at DESC, id DESC);
CREATE INDEX idx_sharing_pool_type_order ON sharing_pool_entries(resource_type, shared_at DESC, id DESC) WHERE revoked_at IS NULL;

CREATE TABLE share_bundles (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    token_digest VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ(6) NOT NULL,
    revoked_at TIMESTAMPTZ(6),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX idx_share_bundles_owner_order ON share_bundles(owner_id, created_at DESC, id DESC);

CREATE TABLE share_bundle_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    bundle_id VARCHAR(36) NOT NULL REFERENCES share_bundles(id) ON DELETE CASCADE,
    resource_type VARCHAR(20) NOT NULL CHECK (resource_type IN ('MARKDOWN', 'SNIPPET', 'DEV_LOG')),
    resource_id VARCHAR(36) NOT NULL,
    resource_generation BIGINT NOT NULL,
    original_title VARCHAR(240) NOT NULL,
    sort_order INTEGER NOT NULL,
    removed_at TIMESTAMPTZ(6),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_bundle_resource UNIQUE(bundle_id, resource_type, resource_id)
);
CREATE INDEX idx_share_bundle_items_order ON share_bundle_items(bundle_id, sort_order, id);
