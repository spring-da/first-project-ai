package com.springda.devnest.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 120) String role,
            @NotBlank @Size(max = 500) String bio,
            @Size(max = 500) String avatarUrl,
            ProfileGender gender
    ) {
    }

    public record Response(
            String id,
            String name,
            String role,
            String bio,
            String avatarUrl,
            ProfileGender gender,
            Instant updatedAt
    ) {
        static Response from(ProfileEntity entity) {
            return new Response(
                    entity.getId(),
                    entity.getName(),
                    entity.getRole(),
                    entity.getBio(),
                    ProfileDtos.avatarUrl(entity),
                    entity.getGender(),
                    entity.getUpdatedAt());
        }
    }

    public static String avatarUrl(ProfileEntity entity) {
        return entity.hasUploadedAvatar()
                ? "/user-avatars/" + entity.getOwnerId()
                : entity.getAvatarUrl();
    }
}
