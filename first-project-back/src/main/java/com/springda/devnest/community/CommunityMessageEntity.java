package com.springda.devnest.community;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_messages")
public class CommunityMessageEntity extends BaseEntity {

    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "image_object_key", length = 512)
    private String imageObjectKey;

    @Column(name = "image_content_type", length = 32)
    private String imageContentType;

    @Column(name = "image_size_bytes")
    private Long imageSizeBytes;

    protected CommunityMessageEntity() {
    }

    public CommunityMessageEntity(
            String parentId,
            String authorId,
            String content,
            String imageObjectKey,
            String imageContentType,
            Long imageSizeBytes
    ) {
        this.parentId = parentId;
        this.authorId = authorId;
        this.content = content;
        this.imageObjectKey = imageObjectKey;
        this.imageContentType = imageContentType;
        this.imageSizeBytes = imageSizeBytes;
    }

    public String getParentId() { return parentId; }
    public String getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public String getImageObjectKey() { return imageObjectKey; }
    public String getImageContentType() { return imageContentType; }
    public Long getImageSizeBytes() { return imageSizeBytes; }
    public boolean hasImage() { return imageObjectKey != null; }
}
