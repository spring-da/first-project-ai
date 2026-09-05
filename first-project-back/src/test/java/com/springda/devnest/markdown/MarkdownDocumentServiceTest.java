package com.springda.devnest.markdown;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import com.springda.devnest.image.MarkdownImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkdownDocumentServiceTest {

    @Mock
    private MarkdownDocumentRepository repository;

    @Mock
    private KnowledgeDomainService domains;

    @Mock
    private MarkdownRevisionRepository revisions;

    @Mock
    private MarkdownImageService imageService;

    @InjectMocks
    private MarkdownDocumentService service;

    @Test
    void listsDatabaseSummariesWithoutLoadingDocumentEntities() {
        var projection = mock(MarkdownDocumentRepository.SummaryProjection.class);
        var now = Instant.parse("2026-08-25T12:00:00Z");
        when(projection.getId()).thenReturn("document-1");
        when(projection.getTitle()).thenReturn("Projection note");
        when(projection.getFileName()).thenReturn("projection.md");
        when(projection.getExcerpt()).thenReturn("# Projection note");
        when(projection.getContentLength()).thenReturn(240);
        when(projection.getFavorite()).thenReturn(true);
        when(projection.getDomainId()).thenReturn("domain-1");
        when(projection.getCreatedAt()).thenReturn(now);
        when(projection.getUpdatedAt()).thenReturn(now);
        when(repository.findSummariesByOwnerId(
                "user-1", MarkdownDocumentDtos.SUMMARY_EXCERPT_LENGTH)).thenReturn(List.of(projection));

        var summaries = service.list("user-1");

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("document-1");
            assertThat(summary.excerpt()).isEqualTo("# Projection note…");
            assertThat(summary.contentLength()).isEqualTo(240);
            assertThat(summary.favorite()).isTrue();
            assertThat(summary.updatedAt()).isEqualTo(now);
        });
        verify(repository, never()).findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc("user-1");
    }

    @Test
    void importsMarkdownWithDerivedTitleAndSafeBaseName() {
        when(repository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new MarkdownDocumentDtos.ImportRequest(List.of(
                new MarkdownDocumentDtos.ImportItem(
                        "../../CON.md", null, "# A useful note\n\nBody", null, null)));

        var response = service.importDocuments("user-1", request);

        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.documents()).singleElement().satisfies(document -> {
            assertThat(document.title()).isEqualTo("A useful note");
            assertThat(document.fileName()).isEqualTo("_CON.md");
            assertThat(document.favorite()).isFalse();
        });
    }

    @Test
    void refusesToReadDocumentOutsideCurrentUserScope() {
        when(repository.findByIdAndOwnerId("document-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("user-2", "document-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("document-1");
    }

    @Test
    void enforcesCombinedImportLimit() {
        var content = "x".repeat(1_000_000);
        var items = List.of(
                item("1.md", content), item("2.md", content), item("3.md", content),
                item("4.md", content), item("5.md", content), item("6.md", "x"));

        assertThatThrownBy(() -> service.importDocuments(
                "user-1", new MarkdownDocumentDtos.ImportRequest(items)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("total limit");
    }

    @Test
    void exportsSafeUniqueZipEntryNames() throws Exception {
        var first = new MarkdownDocumentEntity("user-1", null, "One", "note.md", "first", false);
        var second = new MarkdownDocumentEntity("user-1", null, "Two", "note.md", "second", false);
        when(repository.countByOwnerIdAndDeletedAtIsNull("user-1")).thenReturn(2L);
        when(repository.sumContentOctetsByOwnerId("user-1")).thenReturn(11L);
        when(repository.findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc("user-1"))
                .thenReturn(List.of(first, second));

        var zipBytes = service.exportDocuments(
                "user-1", new MarkdownDocumentDtos.ExportRequest(List.of()));

        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("note.md");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first");
            assertThat(zip.getNextEntry().getName()).isEqualTo("note (2).md");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second");
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    void exportIncludesManagedImagesAndUsesPortableRelativeLinks() throws Exception {
        var imageId = "01234567-89ab-cdef-0123-456789abcdef";
        var document = new MarkdownDocumentEntity(
                "user-1", null, "With image", "image-note.md",
                "# Note\n\n![diagram](/api/v1/markdown-images/" + imageId + ")", false);
        when(repository.countByOwnerIdAndDeletedAtIsNull("user-1")).thenReturn(1L);
        when(repository.sumContentOctetsByOwnerId("user-1")).thenReturn(80L);
        when(repository.findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc("user-1"))
                .thenReturn(List.of(document));
        when(imageService.loadForExport("user-1", java.util.Set.of(imageId))).thenReturn(Map.of(
                imageId, new MarkdownImageService.ExportAsset(
                        imageId, "diagram_uuid.png", new byte[]{1, 2, 3}, "image/png", 3)));

        var zipBytes = service.exportDocuments("user-1", new MarkdownDocumentDtos.ExportRequest(List.of()));

        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("image-note.md");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("![diagram](assets/diagram_uuid.png)")
                    .doesNotContain("/api/v1/markdown-images/");
            assertThat(zip.getNextEntry().getName()).isEqualTo("assets/diagram_uuid.png");
            assertThat(zip.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void zipImportUploadsRelativeImagesAndRewritesMarkdown() throws Exception {
        var archive = zip(Map.of(
                "docs/note.md", "# Imported\n\n![diagram](../assets/diagram.png)".getBytes(StandardCharsets.UTF_8),
                "assets/diagram.png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}));
        when(repository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageService.upload(eq("user-1"), eq("diagram.png"), any(byte[].class)))
                .thenReturn(new MarkdownImageService.UploadResponse(
                        "01234567-89ab-cdef-0123-456789abcdef",
                        "/api/v1/markdown-images/01234567-89ab-cdef-0123-456789abcdef",
                        "image/png", 8));

        var result = service.importFiles("user-1", List.of(new MockMultipartFile(
                "files", "portable.zip", "application/zip", archive)), null);

        assertThat(result.documents()).singleElement().satisfies(document -> {
            assertThat(document.fileName()).isEqualTo("note.md");
            assertThat(document.title()).isEqualTo("Imported");
            assertThat(document.content()).contains(
                    "![diagram](/api/v1/markdown-images/01234567-89ab-cdef-0123-456789abcdef)");
        });
        verify(imageService).upload(eq("user-1"), eq("diagram.png"), any(byte[].class));
    }

    @Test
    void rejectsOversizedExportBeforeLoadingDocumentContent() {
        when(repository.countByOwnerIdAndDeletedAtIsNull("user-1")).thenReturn(1L);
        when(repository.sumContentOctetsByOwnerId("user-1"))
                .thenReturn(100L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.exportDocuments(
                "user-1", new MarkdownDocumentDtos.ExportRequest(List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100 MB");

        verify(repository, never()).findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc("user-1");
    }

    @Test
    void deletingAnArticleOnlyMovesItToTrash() {
        var document = new MarkdownDocumentEntity("user-1", null, "Keep me", "keep.md", "body", false);
        when(repository.findForUpdate("document-1", "user-1")).thenReturn(Optional.of(document));
        service.delete("user-1", "document-1");
        assertThat(document.getDeletedAt()).isNotNull();
        verify(repository).saveAndFlush(document);
        verify(repository, never()).delete(any());
    }

    @Test
    void refusesToReadTrashedContentThroughNormalArticleEndpoint() {
        var document = new MarkdownDocumentEntity("user-1", null, "Deleted", "deleted.md", "body", false);
        document.moveToTrash();
        when(repository.findByIdAndOwnerId("document-1", "user-1")).thenReturn(Optional.of(document));
        assertThatThrownBy(() -> service.get("user-1", "document-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void refusesPermanentDeletionOfAnActiveArticle() {
        var document = new MarkdownDocumentEntity("user-1", null, "Active", "active.md", "body", false);
        when(repository.findForUpdate("document-1", "user-1")).thenReturn(Optional.of(document));
        assertThatThrownBy(() -> service.purge("user-1", "document-1")).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void rejectsStaleDraftBeforeChangingContent() {
        var document = new MarkdownDocumentEntity("user-1", null, "Current", "current.md", "cloud", false);
        when(repository.findForUpdate("document-1", "user-1")).thenReturn(Optional.of(document));
        var request = new MarkdownDocumentDtos.UpdateRequest("Stale", "stale.md", "local", null, false, 9L);
        assertThatThrownBy(() -> service.update("user-1", "document-1", request)).isInstanceOf(ConflictException.class);
        assertThat(document.getContent()).isEqualTo("cloud");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnUpdateWithoutExpectedVersionAtTheServiceBoundary() {
        var document = new MarkdownDocumentEntity("user-1", null, "Current", "current.md", "cloud", false);
        when(repository.findForUpdate("document-1", "user-1")).thenReturn(Optional.of(document));
        var request = new MarkdownDocumentDtos.UpdateRequest("Overwrite", "overwrite.md", "local", null, false, null);

        assertThatThrownBy(() -> service.update("user-1", "document-1", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expectedVersion");
        verify(repository, never()).saveAndFlush(any());
    }

    private MarkdownDocumentDtos.ImportItem item(String fileName, String content) {
        return new MarkdownDocumentDtos.ImportItem(fileName, null, content, null, false);
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
