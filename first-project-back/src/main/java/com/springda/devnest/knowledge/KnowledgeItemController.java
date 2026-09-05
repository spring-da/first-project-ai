package com.springda.devnest.knowledge;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-items")
public class KnowledgeItemController {

    private final KnowledgeItemService knowledgeItems;

    public KnowledgeItemController(KnowledgeItemService knowledgeItems) {
        this.knowledgeItems = knowledgeItems;
    }

    @PatchMapping("/bulk-domain")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void moveToDomain(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody KnowledgeItemDtos.BulkDomainRequest request
    ) {
        knowledgeItems.moveToDomain(ownerId, request);
    }
}
