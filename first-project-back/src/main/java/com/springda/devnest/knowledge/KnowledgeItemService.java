package com.springda.devnest.knowledge;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.log.DevLogService;
import com.springda.devnest.markdown.MarkdownDocumentDtos;
import com.springda.devnest.markdown.MarkdownDocumentService;
import com.springda.devnest.snippet.SnippetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

@Service
public class KnowledgeItemService {

    private final KnowledgeDomainService domains;
    private final MarkdownDocumentService documents;
    private final SnippetService snippets;
    private final DevLogService logs;

    public KnowledgeItemService(
            KnowledgeDomainService domains,
            MarkdownDocumentService documents,
            SnippetService snippets,
            DevLogService logs
    ) {
        this.domains = domains;
        this.documents = documents;
        this.snippets = snippets;
        this.logs = logs;
    }

    @Transactional
    public void moveToDomain(String ownerId, KnowledgeItemDtos.BulkDomainRequest request) {
        var domainId = domains.validateSelection(ownerId, request.domainId());
        var seen = new HashSet<String>();
        var markdownDocuments = new ArrayList<MarkdownDocumentDtos.BulkDomainItem>();
        var snippetIds = new ArrayList<String>();
        var logIds = new ArrayList<String>();

        for (var item : request.items()) {
            var id = item.id().trim();
            if (!seen.add(item.type() + ":" + id.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("批量调整中包含重复知识内容");
            }
            switch (item.type()) {
                case DOCUMENT -> {
                    if (item.expectedVersion() == null) {
                        throw new BadRequestException("批量调整文章必须提供 expectedVersion");
                    }
                    markdownDocuments.add(new MarkdownDocumentDtos.BulkDomainItem(id, item.expectedVersion()));
                }
                case SNIPPET -> snippetIds.add(id);
                case LOG -> logIds.add(id);
            }
        }

        snippetIds.forEach(id -> snippets.moveToDomain(ownerId, id, domainId));
        logIds.forEach(id -> logs.moveToDomain(ownerId, id, domainId));
        if (!markdownDocuments.isEmpty()) {
            documents.moveToDomain(ownerId, new MarkdownDocumentDtos.BulkDomainRequest(markdownDocuments, domainId));
        }
    }
}
