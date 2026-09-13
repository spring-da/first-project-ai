-- Take and verify a recoverable pre-upgrade backup before deploying this migration.
-- Published migrations V17/V18 remain immutable. Legacy log content is intentionally removed.
CREATE TABLE flowcharts (
 id VARCHAR(36) PRIMARY KEY,
 owner_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 creation_key VARCHAR(36) NOT NULL,
 domain_id VARCHAR(36) REFERENCES knowledge_domains(id) ON DELETE SET NULL,
 title VARCHAR(200) NOT NULL,
 is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
 diagram TEXT NOT NULL,
 search_text TEXT NOT NULL,
 node_count INTEGER NOT NULL CHECK (node_count BETWEEN 0 AND 1000),
 edge_count INTEGER NOT NULL CHECK (edge_count BETWEEN 0 AND 2000),
 version BIGINT NOT NULL DEFAULT 0,
 sharing_generation BIGINT NOT NULL DEFAULT 0,
 deleted_at TIMESTAMPTZ(6),
 created_at TIMESTAMPTZ(6) NOT NULL,
 updated_at TIMESTAMPTZ(6) NOT NULL,
 CONSTRAINT uk_flowcharts_creation UNIQUE(owner_id,creation_key)
);
CREATE INDEX idx_flowcharts_owner_updated ON flowcharts(owner_id,is_favorite DESC,updated_at DESC,id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_flowcharts_owner_trash ON flowcharts(owner_id,deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_flowcharts_owner_domain ON flowcharts(owner_id,domain_id);
CREATE TABLE flowchart_revisions (
 id VARCHAR(36) PRIMARY KEY,
 document_id VARCHAR(36) NOT NULL REFERENCES flowcharts(id) ON DELETE CASCADE,
 owner_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 document_version BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL,
 title VARCHAR(200) NOT NULL,
 domain_id VARCHAR(36),
 is_favorite BOOLEAN NOT NULL,
 diagram TEXT NOT NULL,
 created_at TIMESTAMPTZ(6) NOT NULL,
 updated_at TIMESTAMPTZ(6) NOT NULL,
 CONSTRAINT uk_flowchart_revision_version UNIQUE(document_id,document_version)
);
CREATE INDEX idx_flowchart_revisions_history ON flowchart_revisions(owner_id,document_id,document_version DESC);

DELETE FROM knowledge_share_links WHERE resource_type = 'DEV_LOG';
DELETE FROM sharing_pool_entries WHERE resource_type = 'DEV_LOG';
-- Revoke log-only bundles; keep bundle history and mixed-bundle surviving item IDs/tokens.
UPDATE share_bundles b SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP
 WHERE EXISTS (SELECT 1 FROM share_bundle_items i WHERE i.bundle_id=b.id AND i.resource_type='DEV_LOG')
 AND NOT EXISTS (SELECT 1 FROM share_bundle_items i WHERE i.bundle_id=b.id AND i.resource_type<>'DEV_LOG');
DELETE FROM share_bundle_items WHERE resource_type='DEV_LOG';
ALTER TABLE sharing_pool_entries DROP CONSTRAINT sharing_pool_entries_resource_type_check;
ALTER TABLE sharing_pool_entries ADD CONSTRAINT sharing_pool_entries_resource_type_check CHECK (resource_type IN ('MARKDOWN','SNIPPET','FLOWCHART'));
ALTER TABLE share_bundle_items DROP CONSTRAINT share_bundle_items_resource_type_check;
ALTER TABLE share_bundle_items ADD CONSTRAINT share_bundle_items_resource_type_check CHECK (resource_type IN ('MARKDOWN','SNIPPET','FLOWCHART'));
DROP TABLE dev_log_tags;
DROP TABLE dev_logs;
