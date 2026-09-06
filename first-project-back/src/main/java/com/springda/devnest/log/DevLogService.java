package com.springda.devnest.log;

import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import com.springda.devnest.share.KnowledgeResourceType;
import com.springda.devnest.share.KnowledgeShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class DevLogService {

    private final DevLogRepository logs;
    private final KnowledgeDomainService domains;
    private final KnowledgeShareRepository shares;

    public DevLogService(
            DevLogRepository logs,
            KnowledgeDomainService domains,
            KnowledgeShareRepository shares
    ) {
        this.logs = logs;
        this.domains = domains;
        this.shares = shares;
    }

    @Transactional(readOnly = true)
    public List<DevLogDtos.Response> list(String ownerId) {
        return logs.findAllByOwnerIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(ownerId)
                .stream().map(DevLogDtos.Response::from).toList();
    }

    @Transactional
    public DevLogDtos.Response create(String ownerId, DevLogDtos.SaveRequest request) {
        return DevLogDtos.Response.from(logs.save(new DevLogEntity(
                ownerId, domains.validateSelection(ownerId, request.domainId()),
                normalizeTitle(request.title(), request.content()), request.content().trim(),
                request.category(), normalizeTags(request.tags()), request.pinned())));
    }

    @Transactional
    public DevLogDtos.Response update(String ownerId, String id, DevLogDtos.SaveRequest request) {
        var log = find(ownerId, id, false);
        log.update(
                domains.validateSelection(ownerId, request.domainId()),
                normalizeTitle(request.title(), request.content()), request.content().trim(),
                request.category(), normalizeTags(request.tags()), request.pinned());
        return DevLogDtos.Response.from(logs.save(log));
    }

    /** Moves a log to the recycle bin instead of deleting it permanently. */
    @Transactional
    public void delete(String ownerId, String id) {
        var log = find(ownerId, id, false);
        log.moveToTrash();
        logs.save(log);
    }

    @Transactional(readOnly = true)
    public List<DevLogDtos.TrashResponse> trash(String ownerId) {
        return logs.findAllByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(ownerId)
                .stream().map(DevLogDtos.TrashResponse::from).toList();
    }

    @Transactional
    public DevLogDtos.Response restore(String ownerId, String id) {
        var log = find(ownerId, id, true);
        try {
            domains.validateSelection(ownerId, log.getDomainId());
        } catch (NotFoundException ignored) {
            log.moveToDomain(null); // A removed folder must not block recovery.
        }
        log.restoreFromTrash();
        return DevLogDtos.Response.from(logs.save(log));
    }

    @Transactional
    public void purge(String ownerId, String id) {
        var log = find(ownerId, id, true);
        shares.deleteAllByResourceTypeAndResourceIdAndOwnerId(KnowledgeResourceType.DEV_LOG, id, ownerId);
        logs.delete(log);
    }

    @Transactional
    public void moveToDomain(String ownerId, String id, String domainId) {
        var log = find(ownerId, id, false);
        var selectedDomain = domains.validateSelection(ownerId, domainId);
        if (!Objects.equals(log.getDomainId(), selectedDomain)) {
            log.moveToDomain(selectedDomain);
            logs.save(log);
        }
    }

    private DevLogEntity find(String ownerId, String id, boolean trashed) {
        return logs.findByIdAndOwnerId(id, ownerId)
                .filter(log -> (log.getDeletedAt() != null) == trashed)
                .orElseThrow(() -> new NotFoundException("开发日志", id));
    }

    private List<String> normalizeTags(List<String> values) {
        var result = new LinkedHashSet<String>();
        values.stream()
                .map(String::trim)
                .map(value -> value.replaceFirst("^#+", ""))
                .filter(value -> !value.isEmpty())
                .forEach(result::add);
        return List.copyOf(result);
    }

    private String normalizeTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        var firstLine = content.strip().lines().findFirst().orElse("开发日志");
        return firstLine.length() <= 80 ? firstLine : firstLine.substring(0, 80);
    }
}
