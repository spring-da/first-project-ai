package com.springda.devnest.flowchart;

import com.springda.devnest.common.BaseEntity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "flowcharts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "creation_key"}))
public class FlowchartEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "creation_key", nullable = false, length = 36, updatable = false)
    private String creationKey;

    @Column(name = "domain_id", length = 36)
    private String domainId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(nullable = false, columnDefinition = "text")
    private String diagram;

    @Column(name = "search_text", nullable = false, columnDefinition = "text")
    private String searchText;

    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "sharing_generation", nullable = false)
    private long sharingGeneration;

    @Version
    @Column(nullable = false)
    private long version;

    protected FlowchartEntity() {}

    public FlowchartEntity(String owner, String key) {
        ownerId = owner;
        creationKey = key;
    }

    public void update(
            String title, String domainId, boolean favorite, FlowchartValidator.Validated graph) {
        this.title = title;
        this.domainId = domainId;
        this.favorite = favorite;
        diagram = graph.serialized();
        searchText = graph.searchText();
        nodeCount = graph.nodeCount();
        edgeCount = graph.edgeCount();
    }

    public void moveToDomain(String domainId) {
        this.domainId = domainId;
    }

    public void moveToTrash() {
        deletedAt = Instant.now();
        sharingGeneration++;
    }

    public void restoreFromTrash() {
        deletedAt = null;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getCreationKey() {
        return creationKey;
    }

    public String getDomainId() {
        return domainId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public String getDiagram() {
        return diagram;
    }

    public String getSearchText() {
        return searchText;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getSharingGeneration() {
        return sharingGeneration;
    }

    public long getVersion() {
        return version;
    }
}
