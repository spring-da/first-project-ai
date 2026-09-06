package com.springda.devnest.profile;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/user-avatars")
public class UserAvatarController {
    private final ProfileAvatarService avatars;

    public UserAvatarController(ProfileAvatarService avatars) {
        this.avatars = avatars;
    }

    @GetMapping("/{ownerId}")
    ResponseEntity<StreamingResponseBody> read(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String ownerId
    ) {
        var image = avatars.read(jwt.getSubject(), ownerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.VARY, "Authorization")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.writer()::writeTo);
    }
}
