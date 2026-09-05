package com.springda.devnest.profile;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    public void update(String name, String role, String bio, String avatarUrl) {
        this.name = name;
        this.role = role;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
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
}
