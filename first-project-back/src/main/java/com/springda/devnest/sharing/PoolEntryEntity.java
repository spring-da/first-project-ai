package com.springda.devnest.sharing;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sharing_pool_entries", uniqueConstraints =
        @UniqueConstraint(name = "uk_sharing_pool_resource", columnNames = {"owner_id", "resource_type", "resource_id"}))
public class PoolEntryEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false, length = 36) private String ownerId;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 20) private ResourceType resourceType;
    @Column(name = "resource_id", nullable = false, length = 36) private String resourceId;
    @Column(name = "resource_generation", nullable = false) private long resourceGeneration;
    @Column(name = "shared_at", nullable = false) private Instant sharedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected PoolEntryEntity() {}
    public PoolEntryEntity(String ownerId, ResourceType type, String resourceId, long generation, Instant now) {
        this.ownerId = ownerId; this.resourceType = type; this.resourceId = resourceId; publish(generation, now);
    }
    public void publish(long generation, Instant now) { resourceGeneration = generation; sharedAt = now; revokedAt = null; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public String getOwnerId() { return ownerId; }
    public ResourceType getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public long getResourceGeneration() { return resourceGeneration; }
    public Instant getRevokedAt() { return revokedAt; }
}
