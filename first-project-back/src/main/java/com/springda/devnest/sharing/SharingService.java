package com.springda.devnest.sharing;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.image.MarkdownImageService;
import com.springda.devnest.markdown.MarkdownShareTokenService;
import com.springda.devnest.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SharingService {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final PoolEntryRepository pool;
    private final ShareBundleRepository bundles;
    private final ShareBundleItemRepository items;
    private final SharingResources resources;
    private final SharingQueries queries;
    private final UserRepository users;
    private final MarkdownShareTokenService tokens;
    public SharingService(PoolEntryRepository pool, ShareBundleRepository bundles, ShareBundleItemRepository items,
                          SharingResources resources, SharingQueries queries, UserRepository users, MarkdownShareTokenService tokens) {
        this.pool = pool; this.bundles = bundles; this.items = items; this.resources = resources;
        this.queries = queries; this.users = users; this.tokens = tokens;
    }

    @Transactional
    public SharingDtos.PublishResult publish(String owner, SharingDtos.PublishRequest request) {
        lockEnabledOwner(owner);
        var saved = validate(owner, request.items());
        int published = 0, existing = 0; var now = Instant.now();
        for (var entry : saved.entrySet()) {
            var ref = entry.getKey(); long generation = entry.getValue().generation();
            var publication = pool.findByOwnerIdAndResourceTypeAndResourceId(owner, ref.type(), ref.id()).orElse(null);
            if (publication != null && publication.getRevokedAt() == null && publication.getResourceGeneration() == generation) { existing++; continue; }
            if (publication == null) publication = new PoolEntryEntity(owner, ref.type(), ref.id(), generation, now);
            else publication.publish(generation, now);
            pool.save(publication); published++;
        }
        pool.flush();
        return new SharingDtos.PublishResult(published, existing);
    }
    @Transactional(readOnly = true)
    public SharingDtos.Page<SharingDtos.PoolSummary> pool(String owner, int page, int size, String q, ResourceType type, boolean mine) {
        if (mine) requireOwner(owner); else resources.requireEnabled(owner);
        validatePage(page, size); return queries.pool(owner, page, size, search(q), type, mine);
    }
    @Transactional(readOnly = true)
    public SharingDtos.SharedContent poolContent(String reader, String id) {
        resources.requireEnabled(reader); var entry = poolEntry(id);
        return resources.read(entry.getOwnerId(), entry.getResourceType(), entry.getResourceId(), entry.getResourceGeneration(), id);
    }
    @Transactional(readOnly = true)
    public MarkdownImageService.ImageContent poolImage(String reader, String id, String imageId) {
        var content = poolContent(reader, id); return resources.image(poolEntry(id).getOwnerId(), content, imageId);
    }
    @Transactional
    public void revokePool(String owner, SharingDtos.RevokeRequest request) {
        lockOwner(owner); var ids = ids(request.ids()); var entries = pool.findAllByOwnerIdAndIdIn(owner, ids);
        if (entries.size() != ids.size()) throw SharingResources.unavailable();
        var now = Instant.now(); entries.forEach(e -> e.revoke(now)); pool.flush();
    }

    @Transactional
    public SharingDtos.LinkSecret createLink(String owner, SharingDtos.CreateLinkRequest request) {
        lockEnabledOwner(owner);
        var now = Instant.now();
        if (request.expiresAt() == null || !request.expiresAt().isAfter(now) || request.expiresAt().isAfter(now.plus(Duration.ofDays(365))))
            throw new BadRequestException("分享有效期必须晚于当前时间且不超过 365 天");
        if (request.title() == null || request.title().isBlank() || request.title().trim().length() > 200) throw new BadRequestException("请输入 1–200 字的分享标题");
        var saved = validate(owner, request.items()); var token = tokens.createToken();
        var bundle = bundles.saveAndFlush(new ShareBundleEntity(owner, request.title().trim(), tokens.digest(token), request.expiresAt()));
        int order = 0;
        for (var entry : saved.entrySet()) {
            var ref = entry.getKey(); var resource = entry.getValue();
            items.save(new ShareBundleItemEntity(bundle.getId(), ref.type(), ref.id(), resource.generation(), resource.content().title(), order++));
        }
        items.flush();
        return new SharingDtos.LinkSecret(bundle.getId(), token, bundle.getTitle(), bundle.getExpiresAt(), bundle.getCreatedAt());
    }
    @Transactional(readOnly = true)
    public SharingDtos.Page<SharingDtos.LinkSummary> links(String owner, int page, int size, String q) {
        requireOwner(owner); validatePage(page, size); return queries.links(owner, page, size, search(q));
    }
    @Transactional(readOnly = true)
    public SharingDtos.LinkDetail link(String owner, String id) {
        boolean enabled = requireOwner(owner).isEnabled(); ownedBundle(owner, id);
        return new SharingDtos.LinkDetail(queries.link(owner, id), queries.items(owner, id, enabled));
    }
    @Transactional
    public void revokeLinks(String owner, SharingDtos.RevokeRequest request) {
        lockOwner(owner); var ids = ids(request.ids()); var entries = bundles.findAllByOwnerIdAndIdIn(owner, ids);
        if (entries.size() != ids.size()) throw SharingResources.unavailable();
        var now = Instant.now(); entries.forEach(e -> e.revoke(now)); bundles.flush();
    }
    @Transactional
    public void removeItem(String owner, String bundleId, String itemId) {
        lockOwner(owner); ownedBundle(owner, bundleId);
        var item = items.findByIdAndBundleId(itemId, bundleId).orElseThrow(SharingResources::unavailable);
        item.remove(Instant.now()); items.flush();
    }
    @Transactional(readOnly = true)
    public SharingDtos.PublicBundle publicBundle(String token) {
        var bundle = publicLink(token);
        var available = queries.items(bundle.getOwnerId(), bundle.getId(), true).stream().filter(SharingDtos.ManagedItem::available)
                .map(i -> new SharingDtos.PublicItem(i.id(), i.resourceType(), i.title())).toList();
        if (available.isEmpty()) throw SharingResources.unavailable();
        return new SharingDtos.PublicBundle(bundle.getTitle(), bundle.getExpiresAt(), available);
    }
    @Transactional(readOnly = true)
    public SharingDtos.SharedContent publicContent(String token, String itemId) {
        var bundle = publicLink(token); return bundleContent(bundle, itemId);
    }
    @Transactional(readOnly = true)
    public MarkdownImageService.ImageContent publicImage(String token, String itemId, String imageId) {
        var bundle = publicLink(token); return resources.image(bundle.getOwnerId(), bundleContent(bundle, itemId), imageId);
    }
    private SharingDtos.SharedContent bundleContent(ShareBundleEntity bundle, String itemId) {
        var item = items.findByIdAndBundleId(itemId, bundle.getId()).filter(i -> i.getRemovedAt() == null).orElseThrow(SharingResources::unavailable);
        return resources.read(bundle.getOwnerId(), item.getResourceType(), item.getResourceId(), item.getResourceGeneration(), itemId);
    }
    private ShareBundleEntity publicLink(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) throw SharingResources.unavailable();
        var bundle = bundles.findByTokenDigest(tokens.digest(token)).filter(b -> b.isActive(Instant.now())).orElseThrow(SharingResources::unavailable);
        resources.requireEnabled(bundle.getOwnerId()); return bundle;
    }
    private ShareBundleEntity ownedBundle(String owner, String id) { return bundles.findByIdAndOwnerId(id, owner).orElseThrow(SharingResources::unavailable); }
    private PoolEntryEntity poolEntry(String id) { return pool.findById(id).filter(p -> p.getRevokedAt() == null).orElseThrow(SharingResources::unavailable); }
    private void lockOwner(String owner) {
        // Serializes duplicate publications and owner batch mutations without a uniqueness race.
        users.findByIdForUpdate(owner).orElseThrow(SharingResources::unavailable);
    }
    private void lockEnabledOwner(String owner) { lockOwner(owner); resources.requireEnabled(owner); }
    private com.springda.devnest.user.UserEntity requireOwner(String owner) { return users.findById(owner).orElseThrow(SharingResources::unavailable); }
    private Map<SharingDtos.Reference, SharingResources.Saved> validate(String owner, List<SharingDtos.Reference> references) {
        if (references == null || references.isEmpty() || references.size() > 100) throw new BadRequestException("每次请选择 1–100 项内容");
        var saved = new LinkedHashMap<SharingDtos.Reference, SharingResources.Saved>();
        for (var input : references) {
            if (input == null || input.type() == null || input.id() == null || input.id().isBlank() || input.id().trim().length() > 36) throw new BadRequestException("内容标识无效");
            var ref = new SharingDtos.Reference(input.type(), input.id().trim().toLowerCase(Locale.ROOT));
            if (saved.containsKey(ref)) throw new BadRequestException("分享请求包含重复内容");
            saved.put(ref, resources.resolve(owner, ref.type(), ref.id(), ref.id()));
        }
        return saved;
    }
    private List<String> ids(List<String> input) {
        if (input == null || input.isEmpty() || input.size() > 100 || input.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 36)) throw new BadRequestException("每次请选择 1–100 项分享");
        return input.stream().map(String::trim).distinct().toList();
    }
    private void validatePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new BadRequestException("页码须大于等于 0，每页数量为 1–100"); }
    private String search(String q) { if (q == null) return ""; if (q.length() > 200) throw new BadRequestException("搜索内容不能超过 200 字"); return q.trim(); }
}
