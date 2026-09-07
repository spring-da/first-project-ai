-- PostgreSQL baseline for the complete MySQL V16 schema.

-- Only for a new, empty PostgreSQL database. Historical MySQL migrations remain immutable.

CREATE TABLE users (
    id              VARCHAR(36)       NOT NULL,
    email           VARCHAR(190)   NOT NULL,
    password_hash   VARCHAR(100)   NOT NULL,
    display_name    VARCHAR(80)    NOT NULL,
    role            VARCHAR(20)    NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE developer_profiles (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    role            VARCHAR(120)   NOT NULL,
    bio             VARCHAR(500)   NOT NULL,
    avatar_url      VARCHAR(500)   NULL,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_developer_profiles PRIMARY KEY (id),
    CONSTRAINT uk_developer_profiles_owner UNIQUE (owner_id),
    CONSTRAINT fk_developer_profiles_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE tasks (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    title           VARCHAR(240)   NOT NULL,
    is_done         BOOLEAN        NOT NULL DEFAULT FALSE,
    sort_order      INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE projects (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    name            VARCHAR(160)   NOT NULL,
    description     TEXT           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    progress        INT NOT NULL DEFAULT 0,
    next_action     VARCHAR(500)   NOT NULL,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_projects_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE project_tech_stack (
    project_id      VARCHAR(36)       NOT NULL,
    sort_order      INT            NOT NULL,
    tech_name       VARCHAR(80)    NOT NULL,
    CONSTRAINT pk_project_tech_stack PRIMARY KEY (project_id, sort_order),
    CONSTRAINT fk_project_tech_stack_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE code_snippets (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    language        VARCHAR(80)    NOT NULL,
    code            TEXT       NOT NULL,
    is_favorite     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_code_snippets PRIMARY KEY (id),
    CONSTRAINT fk_code_snippets_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE dev_logs (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    title           VARCHAR(240)   NOT NULL,
    content         TEXT       NOT NULL,
    category        VARCHAR(20)    NOT NULL,
    is_pinned       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_dev_logs PRIMARY KEY (id),
    CONSTRAINT fk_dev_logs_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE dev_log_tags (
    log_id          VARCHAR(36)       NOT NULL,
    sort_order      INT            NOT NULL,
    tag             VARCHAR(80)    NOT NULL,
    CONSTRAINT pk_dev_log_tags PRIMARY KEY (log_id, sort_order),
    CONSTRAINT fk_dev_log_tags_log FOREIGN KEY (log_id) REFERENCES dev_logs (id) ON DELETE CASCADE
);

CREATE INDEX idx_tasks_owner_sort ON tasks (owner_id, sort_order, created_at);
CREATE INDEX idx_projects_owner_updated ON projects (owner_id, updated_at);
CREATE INDEX idx_code_snippets_owner_favorite ON code_snippets (owner_id, is_favorite, updated_at);
CREATE INDEX idx_dev_logs_owner_timeline ON dev_logs (owner_id, is_pinned, created_at);
CREATE INDEX idx_dev_log_tags_tag ON dev_log_tags (tag);

CREATE TABLE knowledge_domains (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    description     VARCHAR(240)   NOT NULL DEFAULT '',
    sort_order      INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_knowledge_domains PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_domains_owner_name UNIQUE (owner_id, name),
    CONSTRAINT fk_knowledge_domains_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

ALTER TABLE code_snippets
    ADD COLUMN domain_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_code_snippets_domain
        FOREIGN KEY (domain_id) REFERENCES knowledge_domains (id) ON DELETE SET NULL;

ALTER TABLE dev_logs
    ADD COLUMN domain_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_dev_logs_domain
        FOREIGN KEY (domain_id) REFERENCES knowledge_domains (id) ON DELETE SET NULL;

CREATE INDEX idx_knowledge_domains_owner_sort ON knowledge_domains (owner_id, sort_order, created_at);
CREATE INDEX idx_code_snippets_owner_domain_updated ON code_snippets (owner_id, domain_id, updated_at);
CREATE INDEX idx_dev_logs_owner_domain_updated ON dev_logs (owner_id, domain_id, updated_at);

CREATE TABLE markdown_documents (
    id              VARCHAR(36)       NOT NULL,
    owner_id        VARCHAR(36)       NOT NULL,
    domain_id       VARCHAR(36)       NULL,
    title           VARCHAR(200)   NOT NULL,
    file_name       VARCHAR(255)   NOT NULL,
    content         TEXT       NOT NULL,
    is_favorite     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMPTZ(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_markdown_documents PRIMARY KEY (id),
    CONSTRAINT fk_markdown_documents_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_markdown_documents_domain
        FOREIGN KEY (domain_id) REFERENCES knowledge_domains (id) ON DELETE SET NULL
);

CREATE INDEX idx_markdown_documents_owner_updated ON markdown_documents (owner_id, is_favorite, updated_at);
CREATE INDEX idx_markdown_documents_owner_domain_updated ON markdown_documents (owner_id, domain_id, updated_at);

ALTER TABLE markdown_documents
    ADD COLUMN deleted_at TIMESTAMPTZ(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE markdown_document_revisions (
    id VARCHAR(36) NOT NULL,
    document_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    document_version BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    domain_id VARCHAR(36) NULL,
    is_favorite BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL,
    updated_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_markdown_revision_document FOREIGN KEY (document_id)
        REFERENCES markdown_documents (id) ON DELETE CASCADE,
    CONSTRAINT uk_markdown_revision_version UNIQUE (document_id, document_version)
);

CREATE INDEX idx_markdown_documents_trash ON markdown_documents (owner_id, deleted_at);
CREATE INDEX idx_markdown_revision_owner_document ON markdown_document_revisions (owner_id, document_id, document_version);

CREATE TABLE markdown_images (
    id           VARCHAR(36)     NOT NULL,
    owner_id     VARCHAR(36)     NOT NULL,
    object_key   VARCHAR(512) NOT NULL,
    content_type VARCHAR(32)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_markdown_images PRIMARY KEY (id),
    CONSTRAINT uk_markdown_images_object UNIQUE (object_key),
    CONSTRAINT fk_markdown_images_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_markdown_images_owner ON markdown_images (owner_id, created_at);

ALTER TABLE users
    ADD COLUMN force_password_change BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE registration_invitations (
    id                  VARCHAR(36)      NOT NULL,
    email               VARCHAR(190)  NOT NULL,
    invited_by          VARCHAR(36)      NULL,
    registered_user_id  VARCHAR(36)      NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_registration_invitations PRIMARY KEY (id),
    CONSTRAINT uk_registration_invitations_email UNIQUE (email),
    CONSTRAINT fk_registration_invitations_inviter
        FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_registration_invitations_registered_user
        FOREIGN KEY (registered_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_registration_invitations_registered_user ON registration_invitations (registered_user_id);
CREATE INDEX idx_registration_invitations_created ON registration_invitations (created_at);

ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN login_locked_until TIMESTAMPTZ(6) NULL,
    ADD COLUMN temporary_password_expires_at TIMESTAMPTZ(6) NULL;

ALTER TABLE registration_invitations
    ADD COLUMN token_hash VARCHAR(64) NULL,
    ADD COLUMN expires_at TIMESTAMPTZ(6) NULL,
    ADD CONSTRAINT uk_registration_invitations_token_hash UNIQUE (token_hash);

CREATE INDEX idx_registration_invitations_expires_at ON registration_invitations (expires_at);

ALTER TABLE tasks
    ADD COLUMN scheduled_date DATE NULL,
    ADD COLUMN due_at TIMESTAMPTZ(6) NULL,
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN completed_at TIMESTAMPTZ(6) NULL,
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

CREATE INDEX idx_tasks_owner_views ON tasks (owner_id, is_archived, is_done, scheduled_date, due_at, sort_order);

CREATE TABLE markdown_share_links (
    id           VARCHAR(36)     NOT NULL,
    document_id  VARCHAR(36)     NOT NULL,
    owner_id     VARCHAR(36)     NOT NULL,
    token_digest VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ(6) NOT NULL,
    revoked_at   TIMESTAMPTZ(6) NULL,
    created_at   TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_markdown_share_links PRIMARY KEY (id),
    CONSTRAINT uk_markdown_share_links_token UNIQUE (token_digest),
    CONSTRAINT fk_markdown_share_links_document FOREIGN KEY (document_id)
        REFERENCES markdown_documents (id) ON DELETE CASCADE
);

CREATE INDEX idx_markdown_share_links_owner_document ON markdown_share_links (owner_id, document_id, created_at);
CREATE INDEX idx_markdown_share_links_expiry ON markdown_share_links (expires_at, revoked_at);

ALTER TABLE code_snippets
    ADD COLUMN deleted_at TIMESTAMPTZ(6) NULL;

ALTER TABLE dev_logs
    ADD COLUMN deleted_at TIMESTAMPTZ(6) NULL;

CREATE INDEX idx_code_snippets_trash ON code_snippets (owner_id, deleted_at);
CREATE INDEX idx_dev_logs_trash ON dev_logs (owner_id, deleted_at);

CREATE TABLE admin_audit_events (
    id               VARCHAR(36)  NOT NULL,
    actor_id         VARCHAR(36)  NOT NULL,
    actor_email      VARCHAR(190) NOT NULL,
    target_id        VARCHAR(190) NULL,
    target_label     VARCHAR(190) NULL,
    action           VARCHAR(50)  NOT NULL,
    resource_type    VARCHAR(50)  NOT NULL,
    http_method      VARCHAR(10)  NOT NULL,
    request_path     VARCHAR(500) NOT NULL,
    response_status  INT     NOT NULL,
    success          BOOLEAN      NOT NULL,
    created_at       TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE INDEX idx_admin_audit_created ON admin_audit_events (created_at);
CREATE INDEX idx_admin_audit_actor_created ON admin_audit_events (actor_id, created_at);
CREATE INDEX idx_admin_audit_target_created ON admin_audit_events (target_id, created_at);

CREATE TABLE system_announcements (
    id            VARCHAR(36)  NOT NULL,
    published_by  VARCHAR(36)  NOT NULL,
    title         VARCHAR(160) NOT NULL,
    content       TEXT         NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_system_announcements PRIMARY KEY (id),
    CONSTRAINT fk_system_announcements_publisher FOREIGN KEY (published_by) REFERENCES users (id)
);

CREATE TABLE announcement_reads (
    id               VARCHAR(36)  NOT NULL,
    announcement_id  VARCHAR(36)  NOT NULL,
    reader_id        VARCHAR(36)  NOT NULL,
    created_at       TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_announcement_reads PRIMARY KEY (id),
    CONSTRAINT uk_announcement_reads_reader UNIQUE (announcement_id, reader_id),
    CONSTRAINT fk_announcement_reads_announcement FOREIGN KEY (announcement_id)
        REFERENCES system_announcements (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_reads_reader FOREIGN KEY (reader_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE community_messages (
    id                  VARCHAR(36)  NOT NULL,
    parent_id           VARCHAR(36)  NULL,
    author_id           VARCHAR(36)  NOT NULL,
    content             TEXT         NOT NULL,
    image_object_key    VARCHAR(512) NULL,
    image_content_type  VARCHAR(32)  NULL,
    image_size_bytes    BIGINT       NULL,
    created_at          TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_community_messages PRIMARY KEY (id),
    CONSTRAINT fk_community_messages_parent FOREIGN KEY (parent_id)
        REFERENCES community_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_messages_author FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_community_messages_body CHECK (
        CHAR_LENGTH(TRIM(content)) > 0 OR image_object_key IS NOT NULL
    )
);

CREATE INDEX idx_system_announcements_active_created ON system_announcements (active, created_at);
CREATE INDEX idx_announcement_reads_reader ON announcement_reads (reader_id, created_at);
CREATE INDEX idx_community_messages_feed ON community_messages (parent_id, created_at, id);
CREATE INDEX idx_community_messages_author ON community_messages (author_id, created_at);

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

CREATE TABLE knowledge_share_links (
    id            VARCHAR(36)     NOT NULL,
    resource_type VARCHAR(20)  NOT NULL,
    resource_id   VARCHAR(36)     NOT NULL,
    owner_id      VARCHAR(36)     NOT NULL,
    token_digest  VARCHAR(64) NOT NULL,
    expires_at    TIMESTAMPTZ(6) NOT NULL,
    revoked_at    TIMESTAMPTZ(6) NULL,
    created_at    TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_knowledge_share_links PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_share_links_token UNIQUE (token_digest),
    CONSTRAINT fk_knowledge_share_links_owner FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_share_links_resource ON knowledge_share_links (owner_id, resource_type, resource_id, created_at);
CREATE INDEX idx_knowledge_share_links_expiry ON knowledge_share_links (expires_at, revoked_at);

-- Match the case-insensitive uniqueness used by the application, including concurrent writes.

CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));

CREATE UNIQUE INDEX uk_invitations_email_lower ON registration_invitations (lower(email));

CREATE UNIQUE INDEX uk_domains_owner_name_lower ON knowledge_domains (owner_id, lower(name));

-- Replies can precede their parent in a migration batch; validate at transaction end.

ALTER TABLE community_messages ALTER CONSTRAINT fk_community_messages_parent DEFERRABLE INITIALLY IMMEDIATE;
