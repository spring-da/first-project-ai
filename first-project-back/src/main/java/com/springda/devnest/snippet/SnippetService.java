package com.springda.devnest.snippet;

import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import com.springda.devnest.share.KnowledgeResourceType;
import com.springda.devnest.share.KnowledgeShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class SnippetService {

    private final SnippetRepository snippets;
    private final KnowledgeDomainService domains;
    private final KnowledgeShareRepository shares;

    public SnippetService(
            SnippetRepository snippets,
            KnowledgeDomainService domains,
            KnowledgeShareRepository shares
    ) {
        this.snippets = snippets;
        this.domains = domains;
        this.shares = shares;
    }

    @Transactional(readOnly = true)
    public List<SnippetDtos.Response> list(String ownerId) {
        return snippets.findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc(ownerId)
                .stream().map(SnippetDtos.Response::from).toList();
    }

    @Transactional
    public SnippetDtos.Response create(String ownerId, SnippetDtos.SaveRequest request) {
        return SnippetDtos.Response.from(snippets.save(new SnippetEntity(
                ownerId, domains.validateSelection(ownerId, request.domainId()), request.title().trim(),
                request.language().trim(), request.code().trim(), request.favorite())));
    }

    @Transactional
    public SnippetDtos.Response update(String ownerId, String id, SnippetDtos.SaveRequest request) {
        var snippet = find(ownerId, id, false);
        snippet.update(domains.validateSelection(ownerId, request.domainId()), request.title().trim(),
                request.language().trim(), request.code().trim(), request.favorite());
        return SnippetDtos.Response.from(snippets.save(snippet));
    }

    /** Moves a snippet to the recycle bin instead of deleting it permanently. */
    @Transactional
    public void delete(String ownerId, String id) {
        var snippet = find(ownerId, id, false);
        snippet.moveToTrash();
        snippets.save(snippet);
    }

    @Transactional(readOnly = true)
    public List<SnippetDtos.TrashResponse> trash(String ownerId) {
        return snippets.findAllByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(ownerId)
                .stream().map(SnippetDtos.TrashResponse::from).toList();
    }

    @Transactional
    public SnippetDtos.Response restore(String ownerId, String id) {
        var snippet = find(ownerId, id, true);
        try {
            domains.validateSelection(ownerId, snippet.getDomainId());
        } catch (NotFoundException ignored) {
            snippet.moveToDomain(null); // A removed folder must not block recovery.
        }
        snippet.restoreFromTrash();
        return SnippetDtos.Response.from(snippets.save(snippet));
    }

    @Transactional
    public void purge(String ownerId, String id) {
        var snippet = find(ownerId, id, true);
        shares.deleteAllByResourceTypeAndResourceIdAndOwnerId(KnowledgeResourceType.SNIPPET, id, ownerId);
        snippets.delete(snippet);
    }

    @Transactional
    public void moveToDomain(String ownerId, String id, String domainId) {
        var snippet = find(ownerId, id, false);
        var selectedDomain = domains.validateSelection(ownerId, domainId);
        if (!Objects.equals(snippet.getDomainId(), selectedDomain)) {
            snippet.moveToDomain(selectedDomain);
            snippets.save(snippet);
        }
    }

    private SnippetEntity find(String ownerId, String id, boolean trashed) {
        return snippets.findByIdAndOwnerId(id, ownerId)
                .filter(snippet -> (snippet.getDeletedAt() != null) == trashed)
                .orElseThrow(() -> new NotFoundException("代码片段", id));
    }
}
