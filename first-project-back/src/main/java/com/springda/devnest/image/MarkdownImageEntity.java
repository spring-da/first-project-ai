package com.springda.devnest.image;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "markdown_images")
public class MarkdownImageEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;
    @Column(name = "object_key", nullable = false, length = 512, unique = true)
    private String objectKey;
    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected MarkdownImageEntity() {}
    public MarkdownImageEntity(String ownerId, String objectKey, String contentType, long sizeBytes) {
        this.ownerId = ownerId; this.objectKey = objectKey; this.contentType = contentType; this.sizeBytes = sizeBytes;
    }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
}
