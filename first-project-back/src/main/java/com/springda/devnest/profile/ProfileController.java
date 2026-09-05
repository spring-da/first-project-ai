package com.springda.devnest.profile;

import jakarta.validation.Valid;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
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
}
