package com.springda.devnest.sharing;

import com.springda.devnest.config.WorkspaceOwner;
import com.springda.devnest.image.MarkdownImageService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sharing")
public class SharingController {
    private final SharingService service;
    public SharingController(SharingService service) { this.service = service; }
    @PostMapping("/pool") @ResponseStatus(HttpStatus.CREATED)
    SharingDtos.PublishResult publish(@WorkspaceOwner String owner, @Valid @RequestBody SharingDtos.PublishRequest request) { return service.publish(owner, request); }
    @GetMapping("/pool")
    SharingDtos.Page<SharingDtos.PoolSummary> pool(@WorkspaceOwner String owner, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) ResourceType type, @RequestParam(defaultValue = "false") boolean mine) { return service.pool(owner, page, size, q, type, mine); }
    @GetMapping("/pool/{id}")
    SharingDtos.SharedContent content(@WorkspaceOwner String owner, @PathVariable String id) { return service.poolContent(owner, id); }
    @GetMapping("/pool/{id}/images/{imageId}")
    ResponseEntity<StreamingResponseBody> image(@WorkspaceOwner String owner, @PathVariable String id, @PathVariable String imageId) { return imageResponse(service.poolImage(owner, id, imageId)); }
    @PostMapping("/pool/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokePool(@WorkspaceOwner String owner, @Valid @RequestBody SharingDtos.RevokeRequest request) { service.revokePool(owner, request); }
    @PostMapping("/links") @ResponseStatus(HttpStatus.CREATED)
    SharingDtos.LinkSecret create(@WorkspaceOwner String owner, @Valid @RequestBody SharingDtos.CreateLinkRequest request) { return service.createLink(owner, request); }
    @GetMapping("/links")
    SharingDtos.Page<SharingDtos.LinkSummary> links(@WorkspaceOwner String owner, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String q) { return service.links(owner, page, size, q); }
    @GetMapping("/links/{id}")
    SharingDtos.LinkDetail link(@WorkspaceOwner String owner, @PathVariable String id) { return service.link(owner, id); }
    @DeleteMapping("/links/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@WorkspaceOwner String owner, @PathVariable String id) { service.revokeLinks(owner, new SharingDtos.RevokeRequest(List.of(id))); }
    @PostMapping("/links/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeLinks(@WorkspaceOwner String owner, @Valid @RequestBody SharingDtos.RevokeRequest request) { service.revokeLinks(owner, request); }
    @DeleteMapping("/links/{id}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeItem(@WorkspaceOwner String owner, @PathVariable String id, @PathVariable String itemId) { service.removeItem(owner, id, itemId); }
    static ResponseEntity<StreamingResponseBody> imageResponse(MarkdownImageService.ImageContent image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).contentLength(image.size())
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").body(image.writer()::writeTo);
    }
}
