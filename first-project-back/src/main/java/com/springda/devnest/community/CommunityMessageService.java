package com.springda.devnest.community;

import com.springda.devnest.admin.AdminAuditAction;
import com.springda.devnest.admin.AdminAuditService;
import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.common.SaveRateLimiter;
import com.springda.devnest.image.ImageStorage;
import com.springda.devnest.image.ImageStorageException;
import com.springda.devnest.image.MarkdownImageService;
import com.springda.devnest.image.OssProperties;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommunityMessageService {
    private static final int DEFAULT_PAGE_SIZE = 8;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_REPLY_PAGE_SIZE = 30;
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final CommunityMessageRepository messages;
    private final UserRepository users;
    private final ImageStorage storage;
    private final OssProperties oss;
    private final SaveRateLimiter rateLimiter;
    private final AdminAuditService audit;

    public CommunityMessageService(
            CommunityMessageRepository messages,
            UserRepository users,
            ImageStorage storage,
            OssProperties oss,
            SaveRateLimiter rateLimiter,
            AdminAuditService audit
    ) {
        this.messages = messages;
        this.users = users;
        this.storage = storage;
        this.oss = oss;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CommunityDtos.MessagePage list(String viewerId, String cursor, int requestedSize) {
        var viewer = requireAccount(viewerId);
        var size = pageSize(requestedSize, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        var request = PageRequest.of(0, size + 1);
        List<CommunityMessageEntity> result;
        if (cursor == null || cursor.isBlank()) {
            result = messages.findByParentIdIsNullOrderByCreatedAtDescIdDesc(request);
        } else {
            var boundary = messages.findById(cursor)
                    .filter(message -> message.getParentId() == null)
                    .orElseThrow(() -> new BadRequestException("消息分页游标无效，请刷新后重试。"));
            result = messages.findRootMessagesBefore(boundary.getId(), request);
        }
        var hasMore = result.size() > size;
        var visible = hasMore ? result.subList(0, size) : result;
        var responses = responses(visible, viewer);
        return new CommunityDtos.MessagePage(
                responses,
                hasMore && !visible.isEmpty() ? visible.getLast().getId() : null);
    }

    @Transactional(readOnly = true)
    public CommunityDtos.ReplyPage replies(String viewerId, String messageId, int page, int requestedSize) {
        var viewer = requireAccount(viewerId);
        requireRoot(messageId);
        var safePage = Math.max(0, page);
        var size = pageSize(requestedSize, 10, MAX_REPLY_PAGE_SIZE);
        var result = messages.findByParentIdOrderByCreatedAtAscIdAsc(
                messageId, PageRequest.of(safePage, size));
        return new CommunityDtos.ReplyPage(
                responses(result.getContent(), viewer), result.hasNext() ? safePage + 1 : null);
    }

    @Transactional
    public CommunityDtos.MessageResponse create(
            String authorId,
            String parentId,
            String content,
            MultipartFile image
    ) {
        var author = requireAccount(authorId);
        rateLimiter.check(authorId, "community-message");
        if (parentId != null) requireRoot(parentId);
        var body = normalizeContent(content);
        if (body.isEmpty() && (image == null || image.isEmpty())) {
            throw new BadRequestException("请输入消息内容或选择一张图片。");
        }

        StoredImage stored = image == null || image.isEmpty() ? null : storeImage(authorId, image);
        try {
            var message = messages.saveAndFlush(new CommunityMessageEntity(
                    parentId, authorId, body,
                    stored == null ? null : stored.objectKey(),
                    stored == null ? null : stored.contentType(),
                    stored == null ? null : stored.size()));
            if (stored != null) cleanupObjectIfTransactionRollsBack(stored.objectKey());
            return response(message, author, 0);
        } catch (RuntimeException exception) {
            if (stored != null) deleteAfterFailedUpload(stored.objectKey());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ImageContent readImage(String viewerId, String messageId) {
        requireAccount(viewerId);
        var message = messages.findById(messageId)
                .filter(CommunityMessageEntity::hasImage)
                .orElseThrow(() -> new NotFoundException("消息图片", messageId));
        var objectKey = message.getImageObjectKey();
        return new ImageContent(
                message.getImageSizeBytes(), message.getImageContentType(),
                output -> storage.transferTo(objectKey, output));
    }

    @Transactional
    public void delete(String actorId, String messageId) {
        var actor = requireAccount(actorId);
        var message = messages.findById(messageId)
                .orElseThrow(() -> new NotFoundException("交流消息", messageId));
        if (!actorId.equals(message.getAuthorId()) && actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("只能删除自己发布的消息");
        }
        var objectKeys = messages.findImageObjectKeysForThread(messageId);
        messages.delete(message);
        messages.flush();
        deleteObjectsAfterCommit(objectKeys);
        if (actor.getRole() == UserRole.ADMIN && !actorId.equals(message.getAuthorId())) {
            audit.record(actorId, actor.getEmail(), messageId, "社区消息",
                    AdminAuditAction.COMMUNITY_MESSAGE_MODERATED, "community-messages", "DELETE",
                    "/api/v1/community/messages/" + messageId, 204, true);
        }
    }

    public record ImageContent(long size, String contentType, MarkdownImageService.ImageWriter writer) {
    }

    private List<CommunityDtos.MessageResponse> responses(
            List<CommunityMessageEntity> entities,
            UserEntity viewer
    ) {
        if (entities.isEmpty()) return List.of();
        var authors = authorMap(entities);
        var rootIds = entities.stream().filter(entity -> entity.getParentId() == null)
                .map(CommunityMessageEntity::getId).toList();
        var replyCounts = new HashMap<String, Long>();
        if (!rootIds.isEmpty()) {
            messages.countReplies(rootIds).forEach(count ->
                    replyCounts.put(count.getParentId(), count.getReplyCount()));
        }
        return entities.stream().map(entity -> response(
                entity, authors.get(entity.getAuthorId()), replyCounts.getOrDefault(entity.getId(), 0L), viewer)).toList();
    }

    private CommunityDtos.MessageResponse response(
            CommunityMessageEntity entity,
            UserEntity author,
            long replyCount,
            UserEntity viewer
    ) {
        var authorName = author == null ? "已删除用户" : author.getDisplayName();
        var authorRole = author == null ? UserRole.USER : author.getRole();
        return new CommunityDtos.MessageResponse(
                entity.getId(), entity.getParentId(), entity.getAuthorId(), authorName, authorRole,
                entity.getContent(), entity.hasImage() ? "/api/v1/community/messages/" + entity.getId() + "/image" : null,
                replyCount, viewer.getRole() == UserRole.ADMIN || viewer.getId().equals(entity.getAuthorId()),
                entity.getCreatedAt());
    }

    private CommunityDtos.MessageResponse response(
            CommunityMessageEntity entity,
            UserEntity author,
            long replyCount
    ) {
        return response(entity, author, replyCount, author);
    }

    private Map<String, UserEntity> authorMap(Collection<CommunityMessageEntity> entities) {
        var ids = entities.stream().map(CommunityMessageEntity::getAuthorId).distinct().toList();
        var result = new HashMap<String, UserEntity>();
        users.findAllById(ids).forEach(user -> result.put(user.getId(), user));
        return result;
    }

    private CommunityMessageEntity requireRoot(String messageId) {
        return messages.findById(messageId)
                .filter(message -> message.getParentId() == null)
                .orElseThrow(() -> new NotFoundException("主题消息", messageId));
    }

    private UserEntity requireAccount(String userId) {
        var user = users.findById(userId).orElseThrow(() -> new ForbiddenException("账号不可用"));
        if (!user.isEnabled()) throw new ForbiddenException("账号不可用");
        return user;
    }

    private StoredImage storeImage(String ownerId, MultipartFile image) {
        if (image.getSize() > MarkdownImageService.MAX_IMAGE_BYTES) {
            throw new BadRequestException("单张图片不能超过 20 MB。");
        }
        byte[] bytes;
        try (var input = image.getInputStream()) {
            bytes = input.readNBytes(MarkdownImageService.MAX_IMAGE_BYTES + 1);
        } catch (IOException exception) {
            throw new BadRequestException("无法读取图片，请重新选择。");
        }
        if (bytes.length == 0) throw new BadRequestException("图片是空文件，请重新选择。");
        if (bytes.length > MarkdownImageService.MAX_IMAGE_BYTES) {
            throw new BadRequestException("单张图片不能超过 20 MB。");
        }
        var extension = MarkdownImageService.detectFormat(bytes);
        var contentType = "image/" + (extension.equals("jpg") ? "jpeg" : extension);
        var basePrefix = oss.prefix();
        if (basePrefix == null
                || !basePrefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")
                || basePrefix.length() > 140) {
            throw new ImageStorageException("OSS 图片目录配置不正确，请检查 OSS_IMAGE_PREFIX。");
        }
        var prefix = basePrefix + "/community";
        var objectKey = MarkdownImageService.objectKey(
                prefix, ownerId, LocalDate.now(oss.imageTimeZone()),
                MarkdownImageService.safeFileBase(image.getOriginalFilename()),
                UUID.randomUUID().toString(), extension);
        storage.put(objectKey, bytes, contentType);
        return new StoredImage(objectKey, contentType, (long) bytes.length);
    }

    private String normalizeContent(String content) {
        var value = content == null ? "" : content.strip();
        if (value.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("消息内容不能超过 2000 个字符。");
        }
        return value;
    }

    private int pageSize(int requested, int fallback, int maximum) {
        return requested <= 0 ? fallback : Math.min(requested, maximum);
    }

    private void cleanupObjectIfTransactionRollsBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(key, "消息事务回滚");
            }
        });
    }

    private void deleteAfterFailedUpload(String key) {
        deleteQuietly(key, "消息记录保存失败");
    }

    private void deleteObjectsAfterCommit(List<String> keys) {
        if (keys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                keys.forEach(key -> deleteQuietly(key, "消息删除完成"));
            }
        });
    }

    private void deleteQuietly(String key, String reason) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(CommunityMessageService.class)
                    .warn("{}，但 OSS 图片对象需要人工清理。", reason);
        }
    }

    private record StoredImage(String objectKey, String contentType, Long size) {
    }
}
