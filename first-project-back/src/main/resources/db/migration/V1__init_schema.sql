CREATE TABLE users (
    id              CHAR(36)       NOT NULL,
    email           VARCHAR(190)   NOT NULL,
    password_hash   VARCHAR(100)   NOT NULL,
    display_name    VARCHAR(80)    NOT NULL,
    role            VARCHAR(20)    NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE developer_profiles (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    role            VARCHAR(120)   NOT NULL,
    bio             VARCHAR(500)   NOT NULL,
    avatar_url      VARCHAR(500)   NULL,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_developer_profiles PRIMARY KEY (id),
    CONSTRAINT uk_developer_profiles_owner UNIQUE (owner_id),
    CONSTRAINT fk_developer_profiles_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tasks (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    title           VARCHAR(240)   NOT NULL,
    is_done         BOOLEAN        NOT NULL DEFAULT FALSE,
    sort_order      INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_tasks_owner_sort (owner_id, sort_order, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE projects (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    name            VARCHAR(160)   NOT NULL,
    description     TEXT           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    progress        TINYINT UNSIGNED NOT NULL DEFAULT 0,
    next_action     VARCHAR(500)   NOT NULL,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_projects_progress CHECK (progress BETWEEN 0 AND 100),
    INDEX idx_projects_owner_updated (owner_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_tech_stack (
    project_id      CHAR(36)       NOT NULL,
    sort_order      INT            NOT NULL,
    tech_name       VARCHAR(80)    NOT NULL,
    CONSTRAINT pk_project_tech_stack PRIMARY KEY (project_id, sort_order),
    CONSTRAINT fk_project_tech_stack_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE code_snippets (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    language        VARCHAR(80)    NOT NULL,
    code            LONGTEXT       NOT NULL,
    is_favorite     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_code_snippets PRIMARY KEY (id),
    CONSTRAINT fk_code_snippets_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_code_snippets_owner_favorite (owner_id, is_favorite, updated_at),
    FULLTEXT INDEX ft_code_snippets_search (title, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dev_logs (
    id              CHAR(36)       NOT NULL,
    owner_id        CHAR(36)       NOT NULL,
    title           VARCHAR(240)   NOT NULL,
    content         LONGTEXT       NOT NULL,
    category        VARCHAR(20)    NOT NULL,
    is_pinned       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_dev_logs PRIMARY KEY (id),
    CONSTRAINT fk_dev_logs_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_dev_logs_owner_timeline (owner_id, is_pinned, created_at),
    FULLTEXT INDEX ft_dev_logs_search (title, content)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dev_log_tags (
    log_id          CHAR(36)       NOT NULL,
    sort_order      INT            NOT NULL,
    tag             VARCHAR(80)    NOT NULL,
    CONSTRAINT pk_dev_log_tags PRIMARY KEY (log_id, sort_order),
    CONSTRAINT fk_dev_log_tags_log FOREIGN KEY (log_id) REFERENCES dev_logs (id) ON DELETE CASCADE,
    INDEX idx_dev_log_tags_tag (tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
