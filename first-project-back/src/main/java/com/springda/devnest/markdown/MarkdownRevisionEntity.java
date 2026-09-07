package com.springda.devnest.markdown;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "markdown_document_revisions")
public class MarkdownRevisionEntity extends BaseEntity {
    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;
    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;
    @Column(name = "document_version", nullable = false)
    private long documentVersion;
    @Column(nullable = false, length = 20)
    private String action;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "domain_id", length = 36)
    private String domainId;
    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    protected MarkdownRevisionEntity() {}

    public MarkdownRevisionEntity(MarkdownDocumentEntity document, String action) {
        documentId = document.getId();
        ownerId = document.getOwnerId();
        documentVersion = document.getVersion();
        this.action = action;
        title = document.getTitle();
        fileName = document.getFileName();
        content = document.getContent();
        domainId = document.getDomainId();
        favorite = document.isFavorite();
    }

    public long getDocumentVersion() { return documentVersion; }
    public String getAction() { return action; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getContent() { return content; }
    public String getDomainId() { return domainId; }
    public boolean isFavorite() { return favorite; }
}
