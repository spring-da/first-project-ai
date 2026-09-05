package com.springda.devnest.markdown;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "markdown_share_links")
public class MarkdownShareEntity extends BaseEntity {

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "token_digest", nullable = false, length = 64, unique = true)
    private String tokenDigest;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MarkdownShareEntity() {
    }

    public MarkdownShareEntity(String documentId, String ownerId, String tokenDigest, Instant expiresAt) {
        this.documentId = documentId;
        this.ownerId = ownerId;
        this.tokenDigest = tokenDigest;
        this.expiresAt = expiresAt;
    }

    public String getDocumentId() { return documentId; }
    public String getOwnerId() { return ownerId; }
    public String getTokenDigest() { return tokenDigest; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
