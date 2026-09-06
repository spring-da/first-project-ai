package com.springda.devnest.community;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/community/messages")
public class CommunityMessageController {
    private final CommunityMessageService messages;

    public CommunityMessageController(CommunityMessageService messages) {
        this.messages = messages;
    }

    @GetMapping
    CommunityDtos.MessagePage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "8") int size
    ) {
        return messages.list(jwt.getSubject(), cursor, size);
    }

    @GetMapping("/{messageId}/replies")
    CommunityDtos.ReplyPage replies(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return messages.replies(jwt.getSubject(), messageId, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CommunityDtos.MessageResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String content,
            @RequestParam(required = false) MultipartFile image
    ) {
        return messages.create(jwt.getSubject(), null, content, image);
    }

    @PostMapping(path = "/{messageId}/replies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CommunityDtos.MessageResponse reply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "") String content,
            @RequestParam(required = false) MultipartFile image
    ) {
        return messages.create(jwt.getSubject(), messageId, content, image);
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String messageId) {
        messages.delete(jwt.getSubject(), messageId);
    }

    @GetMapping("/{messageId}/image")
    ResponseEntity<StreamingResponseBody> image(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId
    ) {
        var image = messages.readImage(jwt.getSubject(), messageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600, immutable")
                .header(HttpHeaders.VARY, "Authorization")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.writer()::writeTo);
    }
}
