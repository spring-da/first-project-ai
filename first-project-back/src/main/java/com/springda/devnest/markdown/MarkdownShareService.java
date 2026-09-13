package com.springda.devnest.markdown;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.image.MarkdownImageService;
import com.springda.devnest.image.ManagedMarkdownImages;
import com.springda.devnest.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MarkdownShareService {
    static final Duration MAX_LIFETIME = Duration.ofDays(365);
    static final int MAX_ACTIVE_LINKS = 20;
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final MarkdownShareRepository shares;
    private final MarkdownDocumentRepository documents;
    private final MarkdownShareTokenService tokens;
    private final MarkdownImageService images;
    private final UserRepository users;

    public MarkdownShareService(
            MarkdownShareRepository shares,
            MarkdownDocumentRepository documents,
            MarkdownShareTokenService tokens,
            MarkdownImageService images,
            UserRepository users
    ) {
        this.shares = shares;
        this.documents = documents;
        this.tokens = tokens;
        this.images = images;
        this.users = users;
    }

    @Transactional
    public MarkdownShareDtos.SecretResponse create(
            String ownerId,
            String documentId,
            MarkdownShareDtos.CreateRequest request
    ) {
        if (users.findById(ownerId).filter(user -> user.isEnabled()).isEmpty()) throw invalidShare();
        var document = requireActiveOwnedDocument(ownerId, documentId);
        var now = Instant.now();
        var expiresAt = request.expiresAt();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new BadRequestException("分享链接的失效时间必须晚于当前时间。");
        }
        if (expiresAt.isAfter(now.plus(MAX_LIFETIME))) {
            throw new BadRequestException("分享链接的有效期不能超过 365 天。");
        }
        if (shares.countByDocumentIdAndOwnerIdAndRevokedAtIsNullAndExpiresAtAfterAndResourceGeneration(documentId, ownerId, now, document.getSharingGeneration())
                >= MAX_ACTIVE_LINKS) {
            throw new ConflictException("这篇文章已有过多有效分享链接，请先撤销不再使用的链接。");
        }

        var token = tokens.createToken();
        var share = new MarkdownShareEntity(documentId, ownerId, tokens.digest(token), expiresAt);
        share.captureGeneration(document.getSharingGeneration());
        shares.saveAndFlush(share);
        return MarkdownShareDtos.SecretResponse.from(share, token);
    }

    @Transactional(readOnly = true)
    public List<MarkdownShareDtos.SummaryResponse> list(String ownerId, String documentId) {
        var document = requireActiveOwnedDocument(ownerId, documentId);
        boolean enabled = users.findById(ownerId).filter(user -> user.isEnabled()).isPresent();
        var now = Instant.now();
        return shares.findAllByDocumentIdAndOwnerIdOrderByCreatedAtDesc(documentId, ownerId).stream()
                .map(share -> new MarkdownShareDtos.SummaryResponse(share.getId(), share.getExpiresAt(),
                        share.getCreatedAt(), share.getRevokedAt(), enabled && share.isActive(now)
                        && share.getResourceGeneration() == document.getSharingGeneration()))
                .toList();
    }

    @Transactional
    public void revoke(String ownerId, String documentId, String shareId) {
        requireActiveOwnedDocument(ownerId, documentId);
        var share = shares.findByIdAndDocumentIdAndOwnerId(shareId, documentId, ownerId)
                .orElseThrow(() -> new NotFoundException("分享链接", shareId));
        share.revoke(Instant.now());
        shares.saveAndFlush(share);
    }

    @Transactional(readOnly = true)
    public MarkdownShareDtos.PublicDocumentResponse read(String token) {
        var resolved = resolve(token);
        return MarkdownShareDtos.PublicDocumentResponse.from(resolved.document(), resolved.share());
    }

    @Transactional(readOnly = true)
    public MarkdownImageService.ImageContent readImage(String token, String imageId) {
        var resolved = resolve(token);
        var referencedId = ManagedMarkdownImages.referencedId(resolved.document().getContent(), imageId);
        if (referencedId == null) throw invalidShare();
        return images.read(resolved.document().getOwnerId(), referencedId);
    }

    private ResolvedShare resolve(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) throw invalidShare();
        var share = shares.findByTokenDigest(tokens.digest(token)).orElseThrow(this::invalidShare);
        if (!share.isActive(Instant.now())) throw invalidShare();
        if (users.findById(share.getOwnerId()).filter(user -> user.isEnabled()).isEmpty()) throw invalidShare();
        var document = documents.findByIdAndOwnerId(share.getDocumentId(), share.getOwnerId())
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getSharingGeneration() == share.getResourceGeneration())
                .orElseThrow(this::invalidShare);
        return new ResolvedShare(share, document);
    }

    private MarkdownDocumentEntity requireActiveOwnedDocument(String ownerId, String documentId) {
        return documents.findByIdAndOwnerId(documentId, ownerId)
                .filter(document -> document.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Markdown 文章", documentId));
    }

    private NotFoundException invalidShare() {
        return new NotFoundException("分享链接不存在、已过期或已被撤销");
    }

    private record ResolvedShare(MarkdownShareEntity share, MarkdownDocumentEntity document) {
    }
}
