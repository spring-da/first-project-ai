package com.springda.devnest.snippet;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "code_snippets")
public class SnippetEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "domain_id", length = 36)
    private String domainId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 80)
    private String language;

    @Column(nullable = false, columnDefinition = "longtext")
    private String code;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SnippetEntity() {
    }

    public SnippetEntity(String ownerId, String domainId, String title, String language, String code, boolean favorite) {
        this.ownerId = ownerId;
        update(domainId, title, language, code, favorite);
    }

    public void update(String domainId, String title, String language, String code, boolean favorite) {
        this.domainId = domainId;
        this.title = title;
        this.language = language;
        this.code = code;
        this.favorite = favorite;
    }

    public void moveToDomain(String domainId) { this.domainId = domainId; }

    public String getOwnerId() { return ownerId; }
    public String getDomainId() { return domainId; }
    public String getTitle() { return title; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }
    public boolean isFavorite() { return favorite; }
    public Instant getDeletedAt() { return deletedAt; }

    public void moveToTrash() { deletedAt = Instant.now(); }

    public void restoreFromTrash() { deletedAt = null; }
}
