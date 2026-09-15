-- Existing one-time bundle tokens cannot be recovered from their SHA-256 digest.
-- New tokens are encrypted by the application; legacy links receive an explicit replacement on first copy.
ALTER TABLE share_bundles ADD COLUMN token_ciphertext VARCHAR(192);
