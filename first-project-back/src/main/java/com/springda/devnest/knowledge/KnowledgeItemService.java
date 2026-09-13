package com.springda.devnest.knowledge;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.flowchart.FlowchartService;
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
    private final FlowchartService flowcharts;

    public KnowledgeItemService(
            KnowledgeDomainService domains,
            MarkdownDocumentService documents,
            SnippetService snippets,
            FlowchartService flowcharts
    ) {
        this.domains = domains;
        this.documents = documents;
        this.snippets = snippets;
        this.flowcharts = flowcharts;
    }

    @Transactional
    public void moveToDomain(String ownerId, KnowledgeItemDtos.BulkDomainRequest request) {
        var domainId = domains.validateSelection(ownerId, request.domainId());
        var seen = new HashSet<String>();
        var markdownDocuments = new ArrayList<MarkdownDocumentDtos.BulkDomainItem>();
        var snippetIds = new ArrayList<String>();
        var flowchartItems = new ArrayList<KnowledgeItemDtos.BulkDomainItem>();

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
                case FLOWCHART -> {
                    if (item.expectedVersion() == null) throw new BadRequestException("流程图必须提供 expectedVersion");
                    flowchartItems.add(new KnowledgeItemDtos.BulkDomainItem(item.type(), id, item.expectedVersion()));
                }
            }
        }

        snippetIds.forEach(id -> snippets.moveToDomain(ownerId, id, domainId));
        flowchartItems.stream().sorted(java.util.Comparator.comparing(KnowledgeItemDtos.BulkDomainItem::id)).forEach(item -> flowcharts.moveToDomain(ownerId, item.id(), domainId, item.expectedVersion()));
        if (!markdownDocuments.isEmpty()) {
            documents.moveToDomain(ownerId, new MarkdownDocumentDtos.BulkDomainRequest(markdownDocuments, domainId));
        }
    }
}
