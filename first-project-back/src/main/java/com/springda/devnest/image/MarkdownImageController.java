package com.springda.devnest.image;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/markdown-images")
public class MarkdownImageController {
    private final MarkdownImageService images;
    public MarkdownImageController(MarkdownImageService images) { this.images = images; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MarkdownImageService.UploadResponse upload(@WorkspaceOwner String ownerId, @RequestParam("file") MultipartFile file) {
        return images.upload(ownerId, file);
    }

    @GetMapping("/{id}")
    ResponseEntity<StreamingResponseBody> read(@WorkspaceOwner String ownerId, @PathVariable String id) {
        var image = images.read(ownerId, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                // IDs point to immutable objects. A private cache avoids downloading the same
                // image again when users switch between edit, split and reader modes.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600, immutable")
                .header(HttpHeaders.VARY, "Authorization, X-Workspace-Owner")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.writer()::writeTo);
    }
}
