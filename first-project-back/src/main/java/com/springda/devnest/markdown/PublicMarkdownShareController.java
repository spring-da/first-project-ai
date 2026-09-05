package com.springda.devnest.markdown;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/public/markdown-shares")
public class PublicMarkdownShareController {
    private final MarkdownShareService shares;

    public PublicMarkdownShareController(MarkdownShareService shares) {
        this.shares = shares;
    }

    @GetMapping("/{token}")
    ResponseEntity<MarkdownShareDtos.PublicDocumentResponse> read(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shares.read(token));
    }

    @GetMapping("/{token}/images/{imageId}")
    ResponseEntity<StreamingResponseBody> readImage(@PathVariable String token, @PathVariable String imageId) {
        var image = shares.readImage(token, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.writer()::writeTo);
    }
}
