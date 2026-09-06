package com.springda.devnest.share;

import com.springda.devnest.config.WorkspaceOwner;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/snippets/{resourceId}/shares")
public class SnippetShareController {
    private final KnowledgeShareService shares;

    public SnippetShareController(KnowledgeShareService shares) {
        this.shares = shares;
    }

    @GetMapping
    List<KnowledgeShareDtos.SummaryResponse> list(
            @WorkspaceOwner String ownerId, @PathVariable String resourceId) {
        return shares.list(ownerId, KnowledgeResourceType.SNIPPET, resourceId);
    }

    @PostMapping
    ResponseEntity<KnowledgeShareDtos.SecretResponse> create(
            @WorkspaceOwner String ownerId,
            @PathVariable String resourceId,
            @Valid @RequestBody KnowledgeShareDtos.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(shares.create(ownerId, KnowledgeResourceType.SNIPPET, resourceId, request));
    }

    @DeleteMapping("/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @WorkspaceOwner String ownerId,
            @PathVariable String resourceId,
            @PathVariable String shareId
    ) {
        shares.revoke(ownerId, KnowledgeResourceType.SNIPPET, resourceId, shareId);
    }
}
