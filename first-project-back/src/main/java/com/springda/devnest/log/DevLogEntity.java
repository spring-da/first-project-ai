package com.springda.devnest.log;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dev_logs")
public class DevLogEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "domain_id", length = 36)
    private String domainId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, columnDefinition = "longtext")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LogCategory category;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dev_log_tags", joinColumns = @JoinColumn(name = "log_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "tag", nullable = false, length = 80)
    private List<String> tags = new ArrayList<>();

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DevLogEntity() {
    }

    public DevLogEntity(
            String ownerId,
            String domainId,
            String title,
            String content,
            LogCategory category,
            List<String> tags,
            boolean pinned
    ) {
        this.ownerId = ownerId;
        update(domainId, title, content, category, tags, pinned);
    }

    public void update(String domainId, String title, String content, LogCategory category, List<String> tags, boolean pinned) {
        this.domainId = domainId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags.clear();
        this.tags.addAll(tags);
        this.pinned = pinned;
    }

    public void moveToDomain(String domainId) { this.domainId = domainId; }

    public String getOwnerId() { return ownerId; }
    public String getDomainId() { return domainId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public LogCategory getCategory() { return category; }
    public List<String> getTags() { return List.copyOf(tags); }
    public boolean isPinned() { return pinned; }
    public Instant getDeletedAt() { return deletedAt; }

    public void moveToTrash() { deletedAt = Instant.now(); }

    public void restoreFromTrash() { deletedAt = null; }
}
