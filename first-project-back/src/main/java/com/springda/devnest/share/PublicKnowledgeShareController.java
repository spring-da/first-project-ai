package com.springda.devnest.share;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/knowledge-shares")
public class PublicKnowledgeShareController {
    private final KnowledgeShareService shares;

    public PublicKnowledgeShareController(KnowledgeShareService shares) {
        this.shares = shares;
    }

    @GetMapping("/{token}")
    ResponseEntity<KnowledgeShareDtos.PublicResponse> read(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shares.read(token));
    }
}
