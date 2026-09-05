package com.springda.devnest.profile;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

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
                normalizeAvatarUrl(request.avatarUrl()));
        return ProfileDtos.Response.from(profiles.save(profile));
    }

    private ProfileEntity find(String ownerId) {
        return profiles.findByOwnerId(ownerId)
                .orElseThrow(() -> new NotFoundException("开发者资料", ownerId));
    }

    private String normalizeAvatarUrl(String value) {
        if (value == null || value.isBlank()) return null;
        var normalized = value.trim();
        try {
            var uri = new URI(normalized);
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw invalidAvatarUrl();
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw invalidAvatarUrl();
        }
    }

    private BadRequestException invalidAvatarUrl() {
        return new BadRequestException("头像链接必须是有效的 HTTP 或 HTTPS 地址");
    }
}
