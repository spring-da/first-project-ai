package com.springda.devnest.profile;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "developer_profiles")
public class ProfileEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, unique = true, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 120)
    private String role;

    @Column(nullable = false, length = 500)
    private String bio;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ProfileGender gender;

    @Column(name = "avatar_object_key", length = 512)
    private String avatarObjectKey;

    @Column(name = "avatar_content_type", length = 32)
    private String avatarContentType;

    @Column(name = "avatar_size_bytes")
    private Long avatarSizeBytes;

    protected ProfileEntity() {
    }

    private ProfileEntity(String ownerId, String name, String role, String bio) {
        this.ownerId = ownerId;
        this.name = name;
        this.role = role;
        this.bio = bio;
    }

    public static ProfileEntity initial(String ownerId, String name) {
        return new ProfileEntity(
                ownerId,
                name,
                "Independent Developer",
                "用代码记录成长，把想法构建成作品。");
    }

    public void update(String name, String role, String bio, String avatarUrl, ProfileGender gender) {
        this.name = name;
        this.role = role;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
    }

    public void setUploadedAvatar(String objectKey, String contentType, long sizeBytes) {
        this.avatarUrl = null;
        this.avatarObjectKey = objectKey;
        this.avatarContentType = contentType;
        this.avatarSizeBytes = sizeBytes;
    }

    public void clearAvatar() {
        this.avatarUrl = null;
        this.avatarObjectKey = null;
        this.avatarContentType = null;
        this.avatarSizeBytes = null;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public ProfileGender getGender() {
        return gender;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public Long getAvatarSizeBytes() {
        return avatarSizeBytes;
    }

    public boolean hasUploadedAvatar() {
        return avatarObjectKey != null;
    }
}
