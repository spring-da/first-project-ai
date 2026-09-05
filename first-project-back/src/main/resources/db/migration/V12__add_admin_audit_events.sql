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
    response_status  SMALLINT     NOT NULL,
    success          BOOLEAN      NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_admin_audit_created (created_at),
    INDEX idx_admin_audit_actor_created (actor_id, created_at),
    INDEX idx_admin_audit_target_created (target_id, created_at)
);
