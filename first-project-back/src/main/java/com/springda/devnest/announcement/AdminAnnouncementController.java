package com.springda.devnest.announcement;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/announcements")
public class AdminAnnouncementController {
    private final AnnouncementService announcements;

    public AdminAnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    List<AnnouncementDtos.Response> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return announcements.adminList(jwt.getSubject(), limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AnnouncementDtos.Response publish(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AnnouncementDtos.PublishRequest request
    ) {
        return announcements.publish(jwt.getSubject(), request);
    }

    @DeleteMapping("/{announcementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal Jwt jwt, @PathVariable String announcementId) {
        announcements.archive(jwt.getSubject(), announcementId);
    }
}
