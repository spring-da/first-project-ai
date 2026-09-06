package com.springda.devnest.announcement;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_announcements")
public class SystemAnnouncementEntity extends BaseEntity {

    @Column(name = "published_by", nullable = false, length = 36)
    private String publishedBy;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean active = true;

    protected SystemAnnouncementEntity() {
    }

    public SystemAnnouncementEntity(String publishedBy, String title, String content) {
        this.publishedBy = publishedBy;
        this.title = title;
        this.content = content;
    }

    public String getPublishedBy() { return publishedBy; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isActive() { return active; }
    public void archive() { active = false; }
}
