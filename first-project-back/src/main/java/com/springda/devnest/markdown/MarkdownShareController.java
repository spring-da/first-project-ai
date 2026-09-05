package com.springda.devnest.markdown;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.springda.devnest.config.WorkspaceOwner;
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
@RequestMapping("/api/v1/markdown-documents/{documentId}/shares")
public class MarkdownShareController {
    private final MarkdownShareService shares;

    public MarkdownShareController(MarkdownShareService shares) {
        this.shares = shares;
    }

    @GetMapping
    List<MarkdownShareDtos.SummaryResponse> list(
            @WorkspaceOwner String ownerId,
            @PathVariable String documentId
    ) {
        return shares.list(ownerId, documentId);
    }

    @PostMapping
    ResponseEntity<MarkdownShareDtos.SecretResponse> create(
            @WorkspaceOwner String ownerId,
            @PathVariable String documentId,
            @Valid @RequestBody MarkdownShareDtos.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(shares.create(ownerId, documentId, request));
    }

    @DeleteMapping("/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @WorkspaceOwner String ownerId,
            @PathVariable String documentId,
            @PathVariable String shareId
    ) {
        shares.revoke(ownerId, documentId, shareId);
    }
}
