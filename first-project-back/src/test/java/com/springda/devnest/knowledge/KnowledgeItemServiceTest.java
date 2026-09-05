package com.springda.devnest.knowledge;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.log.DevLogService;
import com.springda.devnest.markdown.MarkdownDocumentDtos;
import com.springda.devnest.markdown.MarkdownDocumentService;
import com.springda.devnest.snippet.SnippetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeItemServiceTest {

    @Mock private KnowledgeDomainService domains;
    @Mock private MarkdownDocumentService documents;
    @Mock private SnippetService snippets;
    @Mock private DevLogService logs;
    @InjectMocks private KnowledgeItemService service;

    @Test
    void movesMixedKnowledgeTypesToOneValidatedDomain() {
        when(domains.validateSelection("owner-1", "domain-2")).thenReturn("domain-2");
        var request = new KnowledgeItemDtos.BulkDomainRequest(List.of(
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.SNIPPET, " snippet-1 ", null),
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.DOCUMENT, " document-1 ", 7L),
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.LOG, " log-1 ", null)
        ), "domain-2");

        service.moveToDomain("owner-1", request);

        verify(snippets).moveToDomain("owner-1", "snippet-1", "domain-2");
        verify(logs).moveToDomain("owner-1", "log-1", "domain-2");
        var captor = ArgumentCaptor.forClass(MarkdownDocumentDtos.BulkDomainRequest.class);
        verify(documents).moveToDomain(org.mockito.ArgumentMatchers.eq("owner-1"), captor.capture());
        assertThat(captor.getValue().domainId()).isEqualTo("domain-2");
        assertThat(captor.getValue().documents()).containsExactly(
                new MarkdownDocumentDtos.BulkDomainItem("document-1", 7L));
    }

    @Test
    void rejectsDuplicateItemsBeforeMovingAnything() {
        when(domains.validateSelection("owner-1", null)).thenReturn(null);
        var request = new KnowledgeItemDtos.BulkDomainRequest(List.of(
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.LOG, "LOG-1", null),
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.LOG, "log-1", null)
        ), null);

        assertThatThrownBy(() -> service.moveToDomain("owner-1", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("重复");
        verify(documents, never()).moveToDomain(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(snippets, never()).moveToDomain(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(logs, never()).moveToDomain(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requiresArticleVersionBeforeMovingAnySelectedItem() {
        when(domains.validateSelection("owner-1", "domain-2")).thenReturn("domain-2");
        var request = new KnowledgeItemDtos.BulkDomainRequest(List.of(
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.SNIPPET, "snippet-1", null),
                new KnowledgeItemDtos.BulkDomainItem(KnowledgeItemType.DOCUMENT, "document-1", null)
        ), "domain-2");

        assertThatThrownBy(() -> service.moveToDomain("owner-1", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expectedVersion");
        verify(snippets, never()).moveToDomain(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(logs, never()).moveToDomain(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
