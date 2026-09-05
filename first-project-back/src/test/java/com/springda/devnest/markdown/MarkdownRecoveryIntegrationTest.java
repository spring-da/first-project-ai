package com.springda.devnest.markdown;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainDtos;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:markdown-recovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItbWFya2Rvd24tcmVjb3ZlcnktdGVzdHM=",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
@Transactional
class MarkdownRecoveryIntegrationTest {
    @Autowired private MarkdownDocumentService service;
    @Autowired private MarkdownDocumentRepository documents;
    @Autowired private MarkdownRevisionRepository revisions;
    @Autowired private KnowledgeDomainService domains;

    @Test
    void versionsPreserveContentAndRestoreWithoutErasingLaterHistory() {
        var created = service.create("owner-one", createRequest("First"));
        assertThat(created.version()).isZero();
        var firstRevision = service.history("owner-one", created.id(), 0).getFirst();
        var updated = service.update("owner-one", created.id(), request("Second", created.version()));
        assertThat(updated.version()).isGreaterThan(created.version());
        assertThat(service.history("owner-one", created.id(), 0)).hasSize(2);
        assertThat(service.revision("owner-one", created.id(), firstRevision.id()).content()).isEqualTo("First");

        var restored = service.restoreRevision("owner-one", created.id(), firstRevision.id(),
                new MarkdownDocumentDtos.RestoreRevisionRequest(updated.version()));
        assertThat(restored.content()).isEqualTo("First");
        assertThat(restored.version()).isGreaterThan(updated.version());
        var history = service.history("owner-one", created.id(), 0);
        assertThat(history).hasSize(3);
        assertThat(history.getFirst().action()).isEqualTo("RESTORED");
        assertThat(service.revision("owner-one", created.id(), history.get(1).id()).content()).isEqualTo("Second");
        service.update("owner-one", created.id(), request("First", restored.version()));
        assertThat(service.history("owner-one", created.id(), 0)).hasSize(3);
    }

    @Test
    void trashIsExcludedFromListingAndExportAndCanBeRecoveredWithHistoryIntact() {
        var document = service.create("owner-one", createRequest("Keep this content"));
        service.delete("owner-one", document.id());
        assertThat(service.list("owner-one")).isEmpty();
        assertThat(service.trash("owner-one")).singleElement().satisfies(item -> assertThat(item.deletedAt()).isNotNull());
        assertThatThrownBy(() -> service.get("owner-one", document.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.exportDocuments("owner-one", new MarkdownDocumentDtos.ExportRequest(List.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.exportDocuments("owner-one", new MarkdownDocumentDtos.ExportRequest(List.of(document.id()))))
                .isInstanceOf(NotFoundException.class);
        var restored = service.restoreFromTrash("owner-one", document.id());
        assertThat(restored.content()).isEqualTo("Keep this content");
        assertThat(restored.deletedAt()).isNull();
        assertThat(service.list("owner-one")).hasSize(1);
        assertThat(service.trash("owner-one")).isEmpty();
        assertThat(service.history("owner-one", document.id(), 0)).hasSize(2);
    }

    @Test
    void permanentDeletionRemovesOnlyTrashedArticleAndItsVersions() {
        var document = service.create("owner-one", createRequest("Delete me"));
        assertThatThrownBy(() -> service.purge("owner-one", document.id())).isInstanceOf(ConflictException.class);
        service.delete("owner-one", document.id());
        service.purge("owner-one", document.id());
        documents.flush();
        assertThat(documents.findById(document.id())).isEmpty();
        assertThat(revisions.count()).isZero();
    }

    @Test
    void anotherAccountCannotAccessHistoryRestoreOrPurge() {
        var document = service.create("owner-one", createRequest("Private note"));
        var revision = service.history("owner-one", document.id(), 0).getFirst();
        assertThat(service.list("owner-two")).isEmpty();
        assertThatThrownBy(() -> service.history("owner-two", document.id(), 0)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.revision("owner-two", document.id(), revision.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.restoreRevision("owner-two", document.id(), revision.id(),
                new MarkdownDocumentDtos.RestoreRevisionRequest(document.version()))).isInstanceOf(NotFoundException.class);
        service.delete("owner-one", document.id());
        assertThat(service.trash("owner-two")).isEmpty();
        assertThatThrownBy(() -> service.restoreFromTrash("owner-two", document.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.purge("owner-two", document.id())).isInstanceOf(NotFoundException.class);
        assertThat(service.trash("owner-one")).hasSize(1);
    }

    @Test
    void staleSaveAndStaleVersionRestoreAreRejected() {
        var document = service.create("owner-one", createRequest("First"));
        var revision = service.history("owner-one", document.id(), 0).getFirst();
        service.update("owner-one", document.id(), request("New cloud content", document.version()));
        assertThatThrownBy(() -> service.update("owner-one", document.id(), request("Stale draft", document.version())))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.restoreRevision("owner-one", document.id(), revision.id(),
                new MarkdownDocumentDtos.RestoreRevisionRequest(document.version()))).isInstanceOf(ConflictException.class);
        assertThat(service.get("owner-one", document.id()).content()).isEqualTo("New cloud content");
    }

    @Test
    void restoringAnOldVersionWithADeletedFolderFallsBackToUnassigned() {
        var domain = domains.create("owner-one", new KnowledgeDomainDtos.SaveRequest("Folder", "", 0));
        var document = service.create("owner-one", new MarkdownDocumentDtos.CreateRequest("Old", "old.md", "Old content", domain.id(), false));
        var revision = service.history("owner-one", document.id(), 0).getFirst();
        var changed = service.update("owner-one", document.id(), request("New content", document.version()));
        domains.delete("owner-one", domain.id());
        var restored = service.restoreRevision("owner-one", document.id(), revision.id(),
                new MarkdownDocumentDtos.RestoreRevisionRequest(changed.version()));
        assertThat(restored.content()).isEqualTo("Old content");
        assertThat(restored.domainId()).isNull();
    }

    @Test
    void historyIsPaginatedAndCannotReadARevisionFromAnotherArticle() {
        var document = service.create("owner-one", createRequest("First"));
        for (int index = 0; index < 22; index++) document = service.update("owner-one", document.id(), request("Edit " + index, document.version()));
        var firstPage = service.history("owner-one", document.id(), 0);
        var secondPage = service.history("owner-one", document.id(), 1);
        assertThat(firstPage).hasSize(20);
        assertThat(secondPage).hasSize(3);
        assertThat(firstPage.getLast().documentVersion()).isGreaterThan(secondPage.getFirst().documentVersion());
        var other = service.create("owner-one", createRequest("Other note"));
        assertThatThrownBy(() -> service.revision("owner-one", other.id(), firstPage.getFirst().id()))
                .isInstanceOf(NotFoundException.class);
    }

    private MarkdownDocumentDtos.UpdateRequest request(String content, Long version) {
        return new MarkdownDocumentDtos.UpdateRequest("Test note", "test.md", content, null, false, version);
    }

    private MarkdownDocumentDtos.CreateRequest createRequest(String content) {
        return new MarkdownDocumentDtos.CreateRequest("Test note", "test.md", content, null, false);
    }
}
