package com.springda.devnest.knowledge;

import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/domains")
public class KnowledgeDomainController {

    private final KnowledgeDomainService domainService;

    public KnowledgeDomainController(KnowledgeDomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping
    List<KnowledgeDomainDtos.Response> list(@WorkspaceOwner String ownerId) {
        return domainService.list(ownerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeDomainDtos.Response create(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody KnowledgeDomainDtos.SaveRequest request
    ) {
        return domainService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    KnowledgeDomainDtos.Response update(
            @WorkspaceOwner String ownerId,
            @PathVariable String id,
            @Valid @RequestBody KnowledgeDomainDtos.SaveRequest request
    ) {
        return domainService.update(ownerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@WorkspaceOwner String ownerId, @PathVariable String id) {
        domainService.delete(ownerId, id);
    }
}
