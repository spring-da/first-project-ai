package com.springda.devnest.announcement;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "announcement_reads", uniqueConstraints =
        @UniqueConstraint(name = "uk_announcement_reads_reader", columnNames = {"announcement_id", "reader_id"}))
public class AnnouncementReadEntity extends BaseEntity {

    @Column(name = "announcement_id", nullable = false, length = 36)
    private String announcementId;

    @Column(name = "reader_id", nullable = false, length = 36)
    private String readerId;

    protected AnnouncementReadEntity() {
    }

    public AnnouncementReadEntity(String announcementId, String readerId) {
        this.announcementId = announcementId;
        this.readerId = readerId;
    }

    public String getAnnouncementId() { return announcementId; }
    public String getReaderId() { return readerId; }
}
