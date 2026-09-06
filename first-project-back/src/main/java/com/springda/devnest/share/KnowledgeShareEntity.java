package com.springda.devnest.share;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_share_links")
public class KnowledgeShareEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private KnowledgeResourceType resourceType;

    @Column(name = "resource_id", nullable = false, length = 36)
    private String resourceId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "token_digest", nullable = false, length = 64, unique = true)
    private String tokenDigest;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected KnowledgeShareEntity() {
    }

    public KnowledgeShareEntity(
            KnowledgeResourceType resourceType,
            String resourceId,
            String ownerId,
            String tokenDigest,
            Instant expiresAt
    ) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ownerId = ownerId;
        this.tokenDigest = tokenDigest;
        this.expiresAt = expiresAt;
    }

    public KnowledgeResourceType getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getOwnerId() { return ownerId; }
    public String getTokenDigest() { return tokenDigest; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
