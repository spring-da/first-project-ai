package com.springda.devnest.markdown;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "markdown_documents")
public class MarkdownDocumentEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "domain_id", length = 36)
    private String domainId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "sharing_generation", nullable = false)
    private long sharingGeneration;

    public long getSharingGeneration() { return sharingGeneration; }

    @Version
    @Column(nullable = false)
    private long version;

    protected MarkdownDocumentEntity() {
    }

    public MarkdownDocumentEntity(
            String ownerId,
            String domainId,
            String title,
            String fileName,
            String content,
            boolean favorite
    ) {
        this.ownerId = ownerId;
        update(domainId, title, fileName, content, favorite);
    }

    public void update(String domainId, String title, String fileName, String content, boolean favorite) {
        this.domainId = domainId;
        this.title = title;
        this.fileName = fileName;
        this.content = content;
        this.favorite = favorite;
    }

    public void moveToDomain(String domainId) { this.domainId = domainId; }

    public String getDomainId() { return domainId; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getContent() { return content; }
    public boolean isFavorite() { return favorite; }
    public String getOwnerId() { return ownerId; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
    public void moveToTrash() { deletedAt = Instant.now(); sharingGeneration++; }
    public void restoreFromTrash() { deletedAt = null; }
}
