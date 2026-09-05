ALTER TABLE users
    ADD COLUMN force_password_change BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;

UPDATE users
SET role = 'ADMIN'
WHERE LOWER(email) = 'springda0099@gmail.com';

CREATE TABLE registration_invitations (
    id                  CHAR(36)      NOT NULL,
    email               VARCHAR(190)  NOT NULL,
    invited_by          CHAR(36)      NULL,
    registered_user_id  CHAR(36)      NULL,
    created_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_registration_invitations PRIMARY KEY (id),
    CONSTRAINT uk_registration_invitations_email UNIQUE (email),
    CONSTRAINT fk_registration_invitations_inviter
        FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_registration_invitations_registered_user
        FOREIGN KEY (registered_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_registration_invitations_registered_user (registered_user_id),
    INDEX idx_registration_invitations_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
