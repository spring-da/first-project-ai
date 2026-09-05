package com.springda.devnest.knowledge;

import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeDomainService {

    private final KnowledgeDomainRepository domains;

    public KnowledgeDomainService(KnowledgeDomainRepository domains) {
        this.domains = domains;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDomainDtos.Response> list(String ownerId) {
        return domains.findAllByOwnerIdOrderBySortOrderAscCreatedAtAsc(ownerId)
                .stream().map(KnowledgeDomainDtos.Response::from).toList();
    }

    @Transactional
    public KnowledgeDomainDtos.Response create(String ownerId, KnowledgeDomainDtos.SaveRequest request) {
        var name = request.name().trim();
        ensureUnique(ownerId, name, null);
        var domain = new KnowledgeDomainEntity(
                ownerId, name, normalizeDescription(request.description()), request.sortOrder());
        return KnowledgeDomainDtos.Response.from(domains.save(domain));
    }

    @Transactional
    public KnowledgeDomainDtos.Response update(
            String ownerId,
            String id,
            KnowledgeDomainDtos.SaveRequest request
    ) {
        var domain = find(ownerId, id);
        var name = request.name().trim();
        ensureUnique(ownerId, name, id);
        domain.update(name, normalizeDescription(request.description()), request.sortOrder());
        return KnowledgeDomainDtos.Response.from(domains.save(domain));
    }

    @Transactional
    public void delete(String ownerId, String id) {
        domains.delete(find(ownerId, id));
    }

    @Transactional(readOnly = true)
    public String validateSelection(String ownerId, String domainId) {
        if (domainId == null || domainId.isBlank()) return null;
        var normalized = domainId.trim().toLowerCase(Locale.ROOT);
        if (!domains.existsByIdAndOwnerId(normalized, ownerId)) {
            throw new NotFoundException("知识领域", normalized);
        }
        return normalized;
    }

    private KnowledgeDomainEntity find(String ownerId, String id) {
        return domains.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("知识领域", id));
    }

    private void ensureUnique(String ownerId, String name, String currentId) {
        var exists = currentId == null
                ? domains.existsByOwnerIdAndNameIgnoreCase(ownerId, name)
                : domains.existsByOwnerIdAndNameIgnoreCaseAndIdNot(ownerId, name, currentId);
        if (exists) throw new ConflictException("已存在同名知识领域：" + name);
    }

    private String normalizeDescription(String value) {
        return value == null ? "" : value.trim();
    }
}
