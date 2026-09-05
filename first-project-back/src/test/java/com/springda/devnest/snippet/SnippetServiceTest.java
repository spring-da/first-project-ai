package com.springda.devnest.snippet;

import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

    @Mock private SnippetRepository repository;
    @Mock private KnowledgeDomainService domains;
    @InjectMocks private SnippetService service;

    @Test
    void movesOnlyAnOwnerScopedSnippet() {
        var snippet = new SnippetEntity("owner-1", "domain-1", "示例", "Java", "return true;", false);
        when(repository.findByIdAndOwnerId("snippet-1", "owner-1")).thenReturn(Optional.of(snippet));
        when(domains.validateSelection("owner-1", "domain-2")).thenReturn("domain-2");

        service.moveToDomain("owner-1", "snippet-1", "domain-2");

        assertThat(snippet.getDomainId()).isEqualTo("domain-2");
        verify(repository).save(snippet);
    }

    @Test
    void hidesAnotherOwnersSnippet() {
        when(repository.findByIdAndOwnerId("snippet-1", "owner-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveToDomain("owner-2", "snippet-1", "domain-2"))
                .isInstanceOf(NotFoundException.class);

        verify(domains, never()).validateSelection("owner-2", "domain-2");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
