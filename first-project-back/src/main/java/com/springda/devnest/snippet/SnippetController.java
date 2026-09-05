package com.springda.devnest.snippet;

import jakarta.validation.Valid;
import com.springda.devnest.common.SaveRateLimiter;
import org.springframework.http.HttpStatus;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/snippets")
public class SnippetController {

    private final SnippetService snippetService;
    private final SaveRateLimiter saveRateLimiter;

    public SnippetController(SnippetService snippetService, SaveRateLimiter saveRateLimiter) {
        this.snippetService = snippetService;
        this.saveRateLimiter = saveRateLimiter;
    }

    @GetMapping
    List<SnippetDtos.Response> list(@WorkspaceOwner String ownerId) {
        return snippetService.list(ownerId);
    }

    @GetMapping("/trash")
    List<SnippetDtos.TrashResponse> trash(@WorkspaceOwner String ownerId) {
        return snippetService.trash(ownerId);
    }

    @PostMapping("/{id}/restore")
    SnippetDtos.Response restore(@WorkspaceOwner String ownerId, @PathVariable String id) {
        return snippetService.restore(ownerId, id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void purge(@WorkspaceOwner String ownerId, @PathVariable String id) {
        snippetService.purge(ownerId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SnippetDtos.Response create(@WorkspaceOwner String ownerId, @Valid @RequestBody SnippetDtos.SaveRequest request) {
        saveRateLimiter.check(ownerId, "snippets");
        return snippetService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    SnippetDtos.Response update(
            @WorkspaceOwner String ownerId,
            @PathVariable String id,
            @Valid @RequestBody SnippetDtos.SaveRequest request
    ) {
        saveRateLimiter.check(ownerId, "snippets");
        return snippetService.update(ownerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@WorkspaceOwner String ownerId, @PathVariable String id) {
        snippetService.delete(ownerId, id);
    }
}
