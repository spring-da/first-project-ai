package com.springda.devnest.share;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.markdown.MarkdownShareTokenService;
import com.springda.devnest.snippet.SnippetEntity;
import com.springda.devnest.snippet.SnippetRepository;
import com.springda.devnest.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class KnowledgeShareService {
    static final Duration MAX_LIFETIME = Duration.ofDays(365);
    static final int MAX_ACTIVE_LINKS = 20;
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final KnowledgeShareRepository shares;
    private final SnippetRepository snippets;
    private final MarkdownShareTokenService tokens;
    private final UserRepository users;

    public KnowledgeShareService(
            KnowledgeShareRepository shares,
            SnippetRepository snippets,
            MarkdownShareTokenService tokens,
            UserRepository users
    ) {
        this.shares = shares;
        this.snippets = snippets;
        this.tokens = tokens;
        this.users = users;
    }

    @Transactional
    public KnowledgeShareDtos.SecretResponse create(
            String ownerId,
            KnowledgeResourceType type,
            String resourceId,
            KnowledgeShareDtos.CreateRequest request
    ) {
        if (users.findById(ownerId).filter(user -> user.isEnabled()).isEmpty()) throw invalidShare();
        var resource = requireActiveResource(ownerId, type, resourceId);
        long generation = resource.getSharingGeneration();
        var now = Instant.now();
        var expiresAt = request.expiresAt();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new BadRequestException("分享链接的失效时间必须晚于当前时间。");
        }
        if (expiresAt.isAfter(now.plus(MAX_LIFETIME))) {
            throw new BadRequestException("分享链接的有效期不能超过 365 天。");
        }
        if (shares.countByResourceTypeAndResourceIdAndOwnerIdAndRevokedAtIsNullAndExpiresAtAfterAndResourceGeneration(
                type, resourceId, ownerId, now, generation) >= MAX_ACTIVE_LINKS) {
            throw new ConflictException("这项内容已有过多有效分享链接，请先撤销不再使用的链接。");
        }

        var token = tokens.createToken();
        var share = new KnowledgeShareEntity(type, resourceId, ownerId, tokens.digest(token), expiresAt);
        share.captureGeneration(generation);
        shares.saveAndFlush(share);
        return KnowledgeShareDtos.SecretResponse.from(share, token);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeShareDtos.SummaryResponse> list(
            String ownerId, KnowledgeResourceType type, String resourceId) {
        var resource = requireActiveResource(ownerId, type, resourceId);
        long generation = resource.getSharingGeneration();
        boolean enabled = users.findById(ownerId).filter(user -> user.isEnabled()).isPresent();
        var now = Instant.now();
        return shares.findAllByResourceTypeAndResourceIdAndOwnerIdOrderByCreatedAtDesc(
                        type, resourceId, ownerId).stream()
                .map(share -> new KnowledgeShareDtos.SummaryResponse(share.getId(), share.getExpiresAt(),
                        share.getCreatedAt(), share.getRevokedAt(), enabled && share.isActive(now)
                        && share.getResourceGeneration() == generation))
                .toList();
    }

    @Transactional
    public void revoke(String ownerId, KnowledgeResourceType type, String resourceId, String shareId) {
        requireActiveResource(ownerId, type, resourceId);
        var share = shares.findByIdAndResourceTypeAndResourceIdAndOwnerId(
                        shareId, type, resourceId, ownerId)
                .orElseThrow(() -> new NotFoundException("分享链接", shareId));
        share.revoke(Instant.now());
        shares.saveAndFlush(share);
    }

    @Transactional(readOnly = true)
    public KnowledgeShareDtos.PublicResponse read(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) throw invalidShare();
        var share = shares.findByTokenDigest(tokens.digest(token)).orElseThrow(this::invalidShare);
        if (!share.isActive(Instant.now())) throw invalidShare();
        if (users.findById(share.getOwnerId()).filter(user -> user.isEnabled()).isEmpty()) throw invalidShare();
        return switch (share.getResourceType()) {
            case SNIPPET -> KnowledgeShareDtos.PublicResponse.from(
                    requireSharedSnippet(share.getOwnerId(), share.getResourceId(), share.getResourceGeneration()), share);
        };
    }

    private SnippetEntity requireActiveResource(String ownerId, KnowledgeResourceType type, String resourceId) {
        return switch (type) {
            case SNIPPET -> snippets.findByIdAndOwnerId(resourceId, ownerId)
                    .filter(item -> item.getDeletedAt() == null)
                    .orElseThrow(() -> new NotFoundException("代码片段", resourceId));
        };
    }

    private SnippetEntity requireSharedSnippet(String ownerId, String resourceId, long generation) {
        return snippets.findByIdAndOwnerId(resourceId, ownerId)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getSharingGeneration() == generation)
                .orElseThrow(this::invalidShare);
    }

    private NotFoundException invalidShare() {
        return new NotFoundException("分享链接不存在、已过期或已被撤销");
    }
}
