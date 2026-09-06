package com.springda.devnest.announcement;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService announcements;

    public AnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    List<AnnouncementDtos.Response> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return announcements.active(jwt.getSubject(), limit);
    }

    @GetMapping("/unread")
    List<AnnouncementDtos.Response> unread(@AuthenticationPrincipal Jwt jwt) {
        return announcements.unread(jwt.getSubject());
    }

    @PostMapping("/{announcementId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String announcementId) {
        announcements.markRead(jwt.getSubject(), announcementId);
    }
}
