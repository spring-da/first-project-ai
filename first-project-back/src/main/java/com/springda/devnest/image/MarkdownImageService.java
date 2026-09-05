package com.springda.devnest.image;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.NotFoundException;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.UUID;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MarkdownImageService {
    public static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final DateTimeFormatter OBJECT_DATE_PATH = DateTimeFormatter.ofPattern("uuuu/MM/dd");
    private final MarkdownImageRepository images;
    private final ImageStorage storage;
    private final OssProperties properties;

    public MarkdownImageService(MarkdownImageRepository images, ImageStorage storage, OssProperties properties) {
        this.images = images; this.storage = storage; this.properties = properties;
    }

    public record UploadResponse(String id, String url, String contentType, long size) {}
    @FunctionalInterface public interface ImageWriter { void writeTo(OutputStream output); }
    public record ImageContent(long size, String contentType, ImageWriter writer) {}
    public record ExportAsset(String id, String fileName, byte[] bytes, String contentType, long size) {}

    @Transactional
    public UploadResponse upload(String ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("请选择或粘贴一张图片。");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new BadRequestException("单张图片不能超过 20 MB。");
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_IMAGE_BYTES + 1); }
        catch (IOException exception) { throw new BadRequestException("无法读取图片，请重新选择。"); }
        return upload(ownerId, file.getOriginalFilename(), bytes);
    }

    @Transactional
    public UploadResponse upload(String ownerId, String originalFileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("图片是空文件，请重新选择。");
        if (bytes.length > MAX_IMAGE_BYTES) throw new BadRequestException("单张图片不能超过 20 MB。");
        String extension = detectFormat(bytes);
        String contentType = "image/" + (extension.equals("jpg") ? "jpeg" : extension);
        String prefix = properties.prefix();
        if (prefix == null || !prefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*") || prefix.length() > 120) {
            throw new ImageStorageException("OSS 图片目录配置不正确，请检查 OSS_IMAGE_PREFIX。");
        }
        if (!ownerId.matches("[a-zA-Z0-9-]{1,36}")) throw new BadRequestException("账户标识无效。");
        String key = objectKey(prefix, ownerId, LocalDate.now(properties.imageTimeZone()),
                safeFileBase(originalFileName), UUID.randomUUID().toString(), extension);
        storage.put(key, bytes, contentType);
        try {
            var image = images.saveAndFlush(new MarkdownImageEntity(ownerId, key, contentType, bytes.length));
            cleanupObjectIfTransactionRollsBack(key);
            return new UploadResponse(image.getId(), "/api/v1/markdown-images/" + image.getId(), contentType, bytes.length);
        } catch (RuntimeException exception) {
            try { storage.delete(key); }
            catch (RuntimeException cleanup) { LoggerFactory.getLogger(getClass()).warn("Image upload metadata failed; OSS cleanup needs retry."); }
            throw new ImageStorageException("图片记录保存失败，请稍后重新上传。");
        }
    }

    public ImageContent read(String ownerId, String id) {
        var image = images.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new NotFoundException("图片", id));
        var objectKey = image.getObjectKey();
        return new ImageContent(image.getSizeBytes(), image.getContentType(),
                output -> storage.transferTo(objectKey, output));
    }

    @Transactional(readOnly = true)
    public Map<String, ExportAsset> loadForExport(String ownerId, Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        var records = images.findAllByOwnerIdAndIdIn(ownerId, ids);
        var result = new LinkedHashMap<String, ExportAsset>();
        for (var image : records) {
            var key = image.getObjectKey();
            var slash = key.lastIndexOf('/');
            var fileName = slash >= 0 ? key.substring(slash + 1) : key;
            result.put(image.getId(), new ExportAsset(
                    image.getId(), fileName, storage.get(key), image.getContentType(), image.getSizeBytes()));
        }
        return result;
    }

    private void cleanupObjectIfTransactionRollsBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                try { storage.delete(key); }
                catch (RuntimeException cleanup) {
                    LoggerFactory.getLogger(MarkdownImageService.class)
                            .warn("Image transaction rolled back; OSS cleanup needs retry.");
                }
            }
        });
    }

    static String detectFormat(byte[] data) {
        if (data.length >= 8 && data[0] == (byte)137 && data[1] == 80 && data[2] == 78 && data[3] == 71
                && data[4] == 13 && data[5] == 10 && data[6] == 26 && data[7] == 10) return "png";
        if (data.length >= 3 && data[0] == (byte)255 && data[1] == (byte)216 && data[2] == (byte)255) return "jpg";
        if (data.length >= 6 && (ascii(data, 0, 6).equals("GIF87a") || ascii(data, 0, 6).equals("GIF89a"))) return "gif";
        if (data.length >= 12 && ascii(data, 0, 4).equals("RIFF") && ascii(data, 8, 4).equals("WEBP")) return "webp";
        throw new BadRequestException("图片格式不支持或文件内容无效，仅支持 PNG、JPG、GIF、WebP，不支持 SVG。");
    }
    static String objectKey(String prefix, String ownerId, LocalDate date, String fileBase, String objectId, String extension) {
        return prefix + "/" + ownerId + "/" + date.format(OBJECT_DATE_PATH) + "/" + fileBase + "_" + objectId + "." + extension;
    }
    static String safeFileBase(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int extensionStart = name.lastIndexOf('.');
        if (extensionStart > 0) name = name.substring(0, extensionStart);
        name = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (name.isBlank()) return "image";
        int codePoints = name.codePointCount(0, name.length());
        return codePoints <= 80 ? name : name.substring(0, name.offsetByCodePoints(0, 80));
    }
    private static String ascii(byte[] bytes, int offset, int length) { return new String(bytes, offset, length, StandardCharsets.US_ASCII); }
}
