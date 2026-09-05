package com.springda.devnest.profile;

import com.springda.devnest.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profiles;

    public ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public ProfileDtos.Response get(String ownerId) {
        return ProfileDtos.Response.from(find(ownerId));
    }

    @Transactional
    public ProfileDtos.Response update(String ownerId, ProfileDtos.UpdateRequest request) {
        var profile = find(ownerId);
        profile.update(
                request.name().trim(),
                request.role().trim(),
                request.bio().trim(),
                normalizeNullable(request.avatarUrl()));
        return ProfileDtos.Response.from(profiles.save(profile));
    }

    private ProfileEntity find(String ownerId) {
        return profiles.findByOwnerId(ownerId)
                .orElseThrow(() -> new NotFoundException("开发者资料", ownerId));
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
