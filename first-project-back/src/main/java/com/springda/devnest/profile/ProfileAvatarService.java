package com.springda.devnest.profile;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.image.ImageStorage;
import com.springda.devnest.image.ImageStorageException;
import com.springda.devnest.image.MarkdownImageService;
import com.springda.devnest.image.OssProperties;
import com.springda.devnest.user.UserRepository;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ProfileAvatarService {
    public static final int MAX_AVATAR_BYTES = 5 * 1024 * 1024;

    private final ProfileRepository profiles;
    private final UserRepository users;
    private final ImageStorage storage;
    private final OssProperties oss;

    public ProfileAvatarService(
            ProfileRepository profiles,
            UserRepository users,
            ImageStorage storage,
            OssProperties oss
    ) {
        this.profiles = profiles;
        this.users = users;
        this.storage = storage;
        this.oss = oss;
    }

    @Transactional
    public ProfileDtos.Response upload(String ownerId, MultipartFile file) {
        var profile = requireProfile(ownerId);
        var bytes = readImage(file);
        var extension = MarkdownImageService.detectFormat(bytes);
        var contentType = "image/" + (extension.equals("jpg") ? "jpeg" : extension);
        var prefix = avatarPrefix();
        var objectKey = MarkdownImageService.objectKey(
                prefix, ownerId, LocalDate.now(oss.imageTimeZone()),
                MarkdownImageService.safeFileBase(file.getOriginalFilename()),
                UUID.randomUUID().toString(), extension);
        storage.put(objectKey, bytes, contentType);
        cleanupIfRollback(objectKey);

        var previousKey = profile.getAvatarObjectKey();
        profile.setUploadedAvatar(objectKey, contentType, bytes.length);
        profiles.saveAndFlush(profile);
        if (previousKey != null && !previousKey.equals(objectKey)) deleteAfterCommit(previousKey);
        return ProfileDtos.Response.from(profile);
    }

    @Transactional
    public ProfileDtos.Response clear(String ownerId) {
        var profile = requireProfile(ownerId);
        var previousKey = profile.getAvatarObjectKey();
        profile.clearAvatar();
        profiles.saveAndFlush(profile);
        if (previousKey != null) deleteAfterCommit(previousKey);
        return ProfileDtos.Response.from(profile);
    }

    @Transactional(readOnly = true)
    public ImageContent read(String viewerId, String ownerId) {
        var viewer = users.findById(viewerId)
                .orElseThrow(() -> new ForbiddenException("账号不可用"));
        if (!viewer.isEnabled()) throw new ForbiddenException("账号不可用");
        var profile = requireProfile(ownerId);
        if (!profile.hasUploadedAvatar()) throw new NotFoundException("用户头像", ownerId);
        var objectKey = profile.getAvatarObjectKey();
        return new ImageContent(
                profile.getAvatarSizeBytes(), profile.getAvatarContentType(),
                output -> storage.transferTo(objectKey, output));
    }

    public record ImageContent(long size, String contentType, MarkdownImageService.ImageWriter writer) {
    }

    private ProfileEntity requireProfile(String ownerId) {
        return profiles.findByOwnerId(ownerId)
                .orElseThrow(() -> new NotFoundException("开发者资料", ownerId));
    }

    private byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("请选择一张头像图片。");
        if (file.getSize() > MAX_AVATAR_BYTES) throw new BadRequestException("头像图片不能超过 5 MB。");
        try (var input = file.getInputStream()) {
            var bytes = input.readNBytes(MAX_AVATAR_BYTES + 1);
            if (bytes.length == 0) throw new BadRequestException("头像图片是空文件，请重新选择。");
            if (bytes.length > MAX_AVATAR_BYTES) throw new BadRequestException("头像图片不能超过 5 MB。");
            return bytes;
        } catch (IOException exception) {
            throw new BadRequestException("无法读取头像图片，请重新选择。");
        }
    }

    private String avatarPrefix() {
        var prefix = oss.prefix();
        if (prefix == null
                || !prefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")
                || prefix.length() > 140) {
            throw new ImageStorageException("OSS 图片目录配置不正确，请检查 OSS_IMAGE_PREFIX。");
        }
        return prefix + "/avatars";
    }

    private void cleanupIfRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(key, "头像事务回滚");
                }
            }
        });
    }

    private void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                deleteQuietly(key, "头像更新完成");
            }
        });
    }

    private void deleteQuietly(String key, String reason) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(ProfileAvatarService.class)
                    .warn("{}，但 OSS 图片对象需要人工清理。", reason);
        }
    }
}
