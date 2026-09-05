package com.springda.devnest.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class KnowledgeItemDtos {

    private KnowledgeItemDtos() {
    }

    public record BulkDomainItem(
            @NotNull KnowledgeItemType type,
            @NotBlank @Size(max = 36) String id,
            @PositiveOrZero Long expectedVersion
    ) {
    }

    public record BulkDomainRequest(
            @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid BulkDomainItem> items,
            @Size(max = 36) String domainId
    ) {
    }
}
