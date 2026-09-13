package com.springda.devnest.sharing;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "share_bundles")
public class ShareBundleEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false, length = 36) private String ownerId;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "token_digest", nullable = false, unique = true, length = 64) private String tokenDigest;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected ShareBundleEntity() {}
    public ShareBundleEntity(String ownerId, String title, String digest, Instant expiresAt) {
        this.ownerId = ownerId; this.title = title; this.tokenDigest = digest; this.expiresAt = expiresAt;
    }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public String getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
