package com.springda.devnest.sharing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/public/share-bundles")
public class PublicShareBundleController {
    private final SharingService service;
    public PublicShareBundleController(SharingService service) { this.service = service; }
    @GetMapping("/{token}")
    SharingDtos.PublicBundle list(@PathVariable String token) { return service.publicBundle(token); }
    @GetMapping("/{token}/items/{itemId}")
    SharingDtos.SharedContent content(@PathVariable String token, @PathVariable String itemId) { return service.publicContent(token, itemId); }
    @GetMapping("/{token}/items/{itemId}/images/{imageId}")
    ResponseEntity<StreamingResponseBody> image(@PathVariable String token, @PathVariable String itemId, @PathVariable String imageId) { return SharingController.imageResponse(service.publicImage(token, itemId, imageId)); }
}
