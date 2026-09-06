package com.springda.devnest.profile;

import jakarta.validation.Valid;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileAvatarService avatars;

    public ProfileController(ProfileService profileService, ProfileAvatarService avatars) {
        this.profileService = profileService;
        this.avatars = avatars;
    }

    @GetMapping
    ProfileDtos.Response get(@WorkspaceOwner String ownerId) {
        return profileService.get(ownerId);
    }

    @PutMapping
    ProfileDtos.Response update(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody ProfileDtos.UpdateRequest request
    ) {
        return profileService.update(ownerId, request);
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProfileDtos.Response uploadAvatar(
            @WorkspaceOwner String ownerId,
            @RequestParam("file") MultipartFile file
    ) {
        return avatars.upload(ownerId, file);
    }

    @DeleteMapping("/avatar")
    ProfileDtos.Response clearAvatar(@WorkspaceOwner String ownerId) {
        return avatars.clear(ownerId);
    }
}
