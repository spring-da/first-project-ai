ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0 AFTER force_password_change,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 AFTER auth_version,
    ADD COLUMN login_locked_until TIMESTAMP(6) NULL AFTER failed_login_attempts,
    ADD COLUMN temporary_password_expires_at TIMESTAMP(6) NULL AFTER login_locked_until;

ALTER TABLE registration_invitations
    ADD COLUMN token_hash CHAR(64) NULL AFTER registered_user_id,
    ADD COLUMN expires_at TIMESTAMP(6) NULL AFTER token_hash,
    ADD CONSTRAINT uk_registration_invitations_token_hash UNIQUE (token_hash),
    ADD INDEX idx_registration_invitations_expires_at (expires_at);

-- V7 invitations did not contain a bearer token and therefore cannot be used safely.
-- Keeping the directory rows lets an administrator rotate them into fresh invitations.
UPDATE registration_invitations
SET expires_at = CURRENT_TIMESTAMP(6)
WHERE registered_user_id IS NULL;
