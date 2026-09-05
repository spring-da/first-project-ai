package com.springda.devnest.markdown;

import jakarta.validation.Valid;
import com.springda.devnest.common.SaveRateLimiter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/markdown-documents")
public class MarkdownDocumentController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final MarkdownDocumentService documentService;
    private final SaveRateLimiter saveRateLimiter;

    public MarkdownDocumentController(MarkdownDocumentService documentService, SaveRateLimiter saveRateLimiter) {
        this.documentService = documentService;
        this.saveRateLimiter = saveRateLimiter;
    }

    @GetMapping
    List<MarkdownDocumentDtos.SummaryResponse> list(@WorkspaceOwner String ownerId) {
        return documentService.list(ownerId);
    }

    @GetMapping("/{id}")
    MarkdownDocumentDtos.Response get(@WorkspaceOwner String ownerId, @PathVariable String id) {
        return documentService.get(ownerId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MarkdownDocumentDtos.Response create(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody MarkdownDocumentDtos.CreateRequest request
    ) {
        saveRateLimiter.check(ownerId, "markdown-documents");
        return documentService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    MarkdownDocumentDtos.Response update(
            @WorkspaceOwner String ownerId,
            @PathVariable String id,
            @Valid @RequestBody MarkdownDocumentDtos.UpdateRequest request
    ) {
        saveRateLimiter.check(ownerId, "markdown-documents");
        return documentService.update(ownerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@WorkspaceOwner String ownerId, @PathVariable String id) {
        documentService.delete(ownerId, id);
    }

    @GetMapping("/trash")
    List<MarkdownDocumentDtos.SummaryResponse> trash(@WorkspaceOwner String ownerId) {
        return documentService.trash(ownerId);
    }

    @PostMapping("/{id}/restore")
    MarkdownDocumentDtos.Response restoreFromTrash(@WorkspaceOwner String ownerId, @PathVariable String id) {
        return documentService.restoreFromTrash(ownerId, id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void purge(@WorkspaceOwner String ownerId, @PathVariable String id) {
        documentService.purge(ownerId, id);
    }

    @GetMapping("/{id}/revisions")
    List<MarkdownDocumentDtos.RevisionSummary> history(@WorkspaceOwner String ownerId, @PathVariable String id,
                                                      @RequestParam(defaultValue = "0") int page) {
        return documentService.history(ownerId, id, page);
    }

    @GetMapping("/{id}/revisions/{revisionId}")
    MarkdownDocumentDtos.RevisionResponse revision(@WorkspaceOwner String ownerId, @PathVariable String id,
                                                   @PathVariable String revisionId) {
        return documentService.revision(ownerId, id, revisionId);
    }

    @PostMapping("/{id}/revisions/{revisionId}/restore")
    MarkdownDocumentDtos.Response restoreRevision(@WorkspaceOwner String ownerId, @PathVariable String id,
                                                  @PathVariable String revisionId,
                                                  @Valid @RequestBody MarkdownDocumentDtos.RestoreRevisionRequest request) {
        return documentService.restoreRevision(ownerId, id, revisionId, request);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MarkdownDocumentDtos.ImportResponse importDocuments(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody MarkdownDocumentDtos.ImportRequest request
    ) {
        return documentService.importDocuments(ownerId, request);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MarkdownDocumentDtos.ImportResponse importFiles(
            @WorkspaceOwner String ownerId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) String domainId
    ) {
        return documentService.importFiles(ownerId, files, domainId);
    }

    @PatchMapping("/bulk-domain")
    List<MarkdownDocumentDtos.Response> moveToDomain(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody MarkdownDocumentDtos.BulkDomainRequest request
    ) {
        return documentService.moveToDomain(ownerId, request);
    }

    @PostMapping(value = "/export", produces = "application/zip")
    ResponseEntity<byte[]> exportDocuments(
            @WorkspaceOwner String ownerId,
            @Valid @RequestBody MarkdownDocumentDtos.ExportRequest request
    ) {
        var zip = documentService.exportDocuments(ownerId, request);
        var disposition = ContentDisposition.attachment()
                .filename("devnest-markdown-export.zip", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(zip.length)
                .body(zip);
    }
}
