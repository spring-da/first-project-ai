package com.springda.devnest.sharing;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "share_bundle_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_bundle_resource", columnNames = {"bundle_id", "resource_type", "resource_id"}))
public class ShareBundleItemEntity extends BaseEntity {
    @Column(name = "bundle_id", nullable = false, length = 36) private String bundleId;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 20) private ResourceType resourceType;
    @Column(name = "resource_id", nullable = false, length = 36) private String resourceId;
    @Column(name = "resource_generation", nullable = false) private long resourceGeneration;
    @Column(name = "original_title", nullable = false, length = 240) private String originalTitle;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "removed_at") private Instant removedAt;
    protected ShareBundleItemEntity() {}
    public ShareBundleItemEntity(String bundleId, ResourceType type, String resourceId, long generation, String title, int order) {
        this.bundleId = bundleId; this.resourceType = type; this.resourceId = resourceId;
        this.resourceGeneration = generation; this.originalTitle = title; this.sortOrder = order;
    }
    public void remove(Instant now) { if (removedAt == null) removedAt = now; }
    public ResourceType getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public long getResourceGeneration() { return resourceGeneration; }
    public Instant getRemovedAt() { return removedAt; }
}
