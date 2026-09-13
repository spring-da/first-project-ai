package com.springda.devnest.flowchart;

import com.springda.devnest.common.*;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import com.springda.devnest.user.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

@Service
public class FlowchartService {
    private final FlowchartRepository documents;
    private final FlowchartRevisionRepository revisions;
    private final KnowledgeDomainService domains;
    private final UserRepository users;
    private final FlowchartValidator validator;
    private final ObjectMapper json;

    public FlowchartService(
            FlowchartRepository documents,
            FlowchartRevisionRepository revisions,
            KnowledgeDomainService domains,
            UserRepository users,
            FlowchartValidator validator,
            ObjectMapper json) {
        this.documents = documents;
        this.revisions = revisions;
        this.domains = domains;
        this.users = users;
        this.validator = validator;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<FlowchartDtos.Summary> list(String owner) {
        return list(owner, "");
    }

    @Transactional(readOnly = true)
    public List<FlowchartDtos.Summary> list(String owner, String query) {
        return documents.summaries(owner, false, search(query));
    }

    @Transactional(readOnly = true)
    public List<FlowchartDtos.Summary> trash(String owner) {
        return documents.summaries(owner, true, "%");
    }

    @Transactional(readOnly = true)
    public FlowchartDtos.Response get(String owner, String id) {
        return response(find(owner, id));
    }

    @Transactional
    public FlowchartDtos.Response create(String owner, FlowchartDtos.CreateRequest request) {
        // Serializes first-write retries across processes; the database unique constraint is a
        // second guard.
        users.findByIdForUpdate(owner).orElseThrow(() -> new NotFoundException("Account", owner));
        var existing =
                documents.findByOwnerIdAndCreationKey(owner, request.creationKey().toString());
        if (existing.isPresent()) {
            if (existing.get().getDeletedAt() != null)
                throw new ConflictException("Creation key belongs to a trashed diagram");
            return response(existing.get());
        }
        var graph = validator.validate(request.diagram());
        var domain = domains.validateSelection(owner, request.domainId());
        var d = new FlowchartEntity(owner, request.creationKey().toString());
        d.update(title(request.title()), domain, request.favorite(), graph);
        documents.saveAndFlush(d);
        checkpoint(d, "CREATED", false);
        return response(d);
    }

    @Transactional
    public FlowchartDtos.Response update(
            String owner, String id, FlowchartDtos.UpdateRequest request) {
        var d = lock(owner, id, false);
        version(d, request.expectedVersion());
        d.update(
                title(request.title()),
                domains.validateSelection(owner, request.domainId()),
                request.favorite(),
                validator.validate(request.diagram()));
        documents.saveAndFlush(d);
        checkpoint(
                d,
                request.saveMode() == FlowchartDtos.SaveMode.AUTO ? "AUTO" : "MANUAL",
                request.saveMode() == FlowchartDtos.SaveMode.AUTO);
        return response(d);
    }

    @Transactional
    public void delete(String owner, String id) {
        var d = lock(owner, id, false);
        d.moveToTrash();
        documents.saveAndFlush(d);
    }

    @Transactional
    public FlowchartDtos.Response restore(String owner, String id) {
        var d = lock(owner, id, true);
        d.restoreFromTrash();
        documents.saveAndFlush(d);
        checkpoint(d, "RECOVERED", false);
        return response(d);
    }

    @Transactional
    public void purge(String owner, String id) {
        var d = lock(owner, id, true);
        revisions.deleteByDocumentIdAndOwnerId(id, owner);
        documents.delete(d);
        documents.flush();
    }

    @Transactional
    public void moveToDomain(String owner, String id, String domainId, Long expectedVersion) {
        var d = lock(owner, id, false);
        version(d, expectedVersion);
        d.moveToDomain(domains.validateSelection(owner, domainId));
        documents.saveAndFlush(d);
        checkpoint(d, "MOVED", false);
    }

    @Transactional(readOnly = true)
    public List<FlowchartDtos.RevisionSummary> history(String owner, String id, int page) {
        find(owner, id);
        if (page < 0) throw new BadRequestException("Page cannot be negative");
        return revisions.summaries(id, owner, PageRequest.of(page, 20));
    }

    @Transactional(readOnly = true)
    public FlowchartDtos.RevisionResponse revision(String owner, String id, String revisionId) {
        find(owner, id);
        var r = findRevision(owner, id, revisionId);
        return new FlowchartDtos.RevisionResponse(
                r.getId(),
                r.getDocumentVersion(),
                r.getTitle(),
                r.getAction(),
                r.getCreatedAt(),
                r.getDomainId(),
                r.isFavorite(),
                json.readTree(r.getDiagram()));
    }

    @Transactional
    public FlowchartDtos.Response restoreRevision(
            String owner, String id, String revisionId, FlowchartDtos.RestoreRequest request) {
        var d = lock(owner, id, false);
        version(d, request.expectedVersion());
        var r = findRevision(owner, id, revisionId);
        String domain;
        try {
            domain = domains.validateSelection(owner, r.getDomainId());
        } catch (NotFoundException ignored) {
            domain = null;
        }
        d.update(
                r.getTitle(),
                domain,
                r.isFavorite(),
                validator.validate(json.readTree(r.getDiagram())));
        documents.saveAndFlush(d);
        checkpoint(d, "RESTORED", false);
        return response(d);
    }

    private void checkpoint(FlowchartEntity d, String action, boolean automatic) {
        var latest =
                revisions.findFirstByDocumentIdAndOwnerIdOrderByDocumentVersionDesc(
                        d.getId(), d.getOwnerId());
        if (latest.isPresent()
                && (latest.get().matches(d)
                        || (automatic
                                && latest.get()
                                        .getCreatedAt()
                                        .plus(Duration.ofMinutes(5))
                                        .isAfter(Instant.now())))) return;
        revisions.saveAndFlush(new FlowchartRevisionEntity(d, action));
        var old = revisions.summaries(d.getId(), d.getOwnerId(), PageRequest.of(1, 100));
        if (!old.isEmpty())
            revisions.deleteAllByIdInBatch(
                    old.stream().map(FlowchartDtos.RevisionSummary::id).toList());
    }

    private FlowchartRevisionEntity findRevision(String owner, String id, String revisionId) {
        return revisions
                .findByIdAndDocumentIdAndOwnerId(revisionId, id, owner)
                .orElseThrow(() -> new NotFoundException("Revision", revisionId));
    }

    private FlowchartEntity find(String owner, String id) {
        return documents
                .findByIdAndOwnerId(id, owner)
                .filter(d -> d.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Flowchart", id));
    }

    private FlowchartEntity lock(String owner, String id, boolean trash) {
        var d =
                documents
                        .findForUpdate(id, owner)
                        .orElseThrow(() -> new NotFoundException("Flowchart", id));
        if ((d.getDeletedAt() != null) != trash)
            throw new ConflictException(
                    trash ? "Flowchart is not in trash" : "Flowchart is in trash");
        return d;
    }

    private void version(FlowchartEntity d, Long expected) {
        if (expected == null || expected < 0)
            throw new BadRequestException("expectedVersion is required");
        if (expected != d.getVersion())
            throw new ConflictException(
                    "Flowchart has a newer cloud version; keep your local draft and reload or save"
                        + " a copy");
    }

    private String title(String value) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > 200)
            throw new BadRequestException("Title must contain 1 to 200 characters");
        return value.strip();
    }

    private String search(String q) {
        if (q == null) q = "";
        return "%"
                + q.strip()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                + "%";
    }

    private FlowchartDtos.Response response(FlowchartEntity d) {
        String excerpt = d.getSearchText();
        if (excerpt.length() > 240) excerpt = excerpt.substring(0, 240);
        return new FlowchartDtos.Response(
                d.getId(),
                d.getTitle(),
                d.getDomainId(),
                d.isFavorite(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getVersion(),
                d.getDeletedAt(),
                d.getNodeCount(),
                d.getEdgeCount(),
                excerpt,
                json.readTree(d.getDiagram()));
    }
}
