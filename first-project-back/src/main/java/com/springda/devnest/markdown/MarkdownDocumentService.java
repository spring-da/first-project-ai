package com.springda.devnest.markdown;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import com.springda.devnest.image.MarkdownImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class MarkdownDocumentService {

    private static final int MAX_IMPORT_TOTAL_LENGTH = 5_000_000;
    private static final int MAX_IMPORT_MARKDOWN_BYTES = 1024 * 1024;
    private static final int MAX_IMPORT_ARCHIVE_BYTES = 100 * 1024 * 1024;
    private static final int MAX_IMPORT_EXPANDED_BYTES = 120 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 500;
    private static final int MAX_EXPORT_UNCOMPRESSED_BYTES = 100 * 1024 * 1024;
    private static final Pattern HEADING = Pattern.compile("^\\s*#\\s+(.+?)(?:\\s+#+)?\\s*$");
    private static final Pattern UNSAFE_FILE_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}<>:\"/\\\\|?*]");
    private static final Pattern UNSAFE_TITLE_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}\\s]+");
    private static final Pattern MARKDOWN_IMAGE_LINK = Pattern.compile("(!\\[[^\\]\\r\\n]*\\]\\()([^\\s)]+)(\\))");
    private static final Pattern MANAGED_IMAGE_TARGET = Pattern.compile(
            "^/api/v1/markdown-images/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final MarkdownDocumentRepository documents;
    private final KnowledgeDomainService domains;
    private final MarkdownRevisionRepository revisions;
    private final MarkdownImageService imageService;

    public MarkdownDocumentService(MarkdownDocumentRepository documents, KnowledgeDomainService domains,
                                   MarkdownRevisionRepository revisions, MarkdownImageService imageService) {
        this.documents = documents;
        this.domains = domains;
        this.revisions = revisions;
        this.imageService = imageService;
    }

    private record PendingImport(String fileName, String content) {}

    @Transactional(readOnly = true)
    public List<MarkdownDocumentDtos.SummaryResponse> list(String ownerId) {
        return documents.findSummariesByOwnerId(ownerId, MarkdownDocumentDtos.SUMMARY_EXCERPT_LENGTH)
                .stream().map(MarkdownDocumentDtos.SummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MarkdownDocumentDtos.Response get(String ownerId, String id) {
        return MarkdownDocumentDtos.Response.from(find(ownerId, id));
    }

    @Transactional
    public MarkdownDocumentDtos.Response create(String ownerId, MarkdownDocumentDtos.CreateRequest request) {
        var document = new MarkdownDocumentEntity(
                ownerId,
                domains.validateSelection(ownerId, request.domainId()),
                normalizeRequiredTitle(request.title()),
                normalizeFileName(request.fileName()),
                normalizeContent(request.content()),
                request.favorite());
        return saveSnapshot(document, "CREATED");
    }

    @Transactional
    public MarkdownDocumentDtos.Response update(
            String ownerId,
            String id,
            MarkdownDocumentDtos.UpdateRequest request
    ) {
        var document = findForUpdate(ownerId, id, false);
        checkVersion(document, request.expectedVersion());
        var domainId = domains.validateSelection(ownerId, request.domainId());
        var title = normalizeRequiredTitle(request.title());
        var fileName = normalizeFileName(request.fileName());
        var content = normalizeContent(request.content());
        if (Objects.equals(document.getDomainId(), domainId) && document.getTitle().equals(title)
                && document.getFileName().equals(fileName) && document.getContent().equals(content)
                && document.isFavorite() == request.favorite()) {
            return MarkdownDocumentDtos.Response.from(document);
        }
        document.update(
                domainId, title, fileName, content,
                request.favorite());
        return saveSnapshot(document, "UPDATED");
    }

    @Transactional
    public void delete(String ownerId, String id) {
        var document = findForUpdate(ownerId, id, false);
        document.moveToTrash();
        documents.saveAndFlush(document);
    }

    @Transactional(readOnly = true)
    public List<MarkdownDocumentDtos.SummaryResponse> trash(String ownerId) {
        return documents.findTrashByOwnerId(ownerId).stream().map(MarkdownDocumentDtos.SummaryResponse::from).toList();
    }

    @Transactional
    public MarkdownDocumentDtos.Response restoreFromTrash(String ownerId, String id) {
        var document = findForUpdate(ownerId, id, true);
        document.restoreFromTrash();
        return saveSnapshot(document, "RECOVERED");
    }

    @Transactional
    public void purge(String ownerId, String id) {
        var document = findForUpdate(ownerId, id, true);
        revisions.deleteByDocumentIdAndOwnerId(id, ownerId);
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public List<MarkdownDocumentDtos.RevisionSummary> history(String ownerId, String id, int page) {
        find(ownerId, id);
        if (page < 0) throw new BadRequestException("页码不能小于 0");
        return revisions.findByDocumentIdAndOwnerIdOrderByDocumentVersionDesc(id, ownerId, PageRequest.of(page, 20))
                .stream().map(MarkdownDocumentDtos.RevisionSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public MarkdownDocumentDtos.RevisionResponse revision(String ownerId, String id, String revisionId) {
        find(ownerId, id);
        return MarkdownDocumentDtos.RevisionResponse.from(findRevision(ownerId, id, revisionId));
    }

    @Transactional
    public MarkdownDocumentDtos.Response restoreRevision(String ownerId, String id, String revisionId,
                                                         MarkdownDocumentDtos.RestoreRevisionRequest request) {
        var document = findForUpdate(ownerId, id, false);
        checkVersion(document, request.expectedVersion());
        var revision = findRevision(ownerId, id, revisionId);
        if (revision.getDocumentVersion() == document.getVersion()) {
            return MarkdownDocumentDtos.Response.from(document);
        }
        String domainId;
        try {
            domainId = domains.validateSelection(ownerId, revision.getDomainId());
        } catch (NotFoundException ignored) {
            domainId = null; // A removed folder must not prevent content recovery.
        }
        document.update(domainId, revision.getTitle(), revision.getFileName(), revision.getContent(), revision.isFavorite());
        // A restore creates a new snapshot. Earlier versions are never rewritten.
        // If identical content is restored, Hibernate need not create a new version.
        long previousVersion = document.getVersion();
        documents.saveAndFlush(document);
        if (document.getVersion() != previousVersion) revisions.save(new MarkdownRevisionEntity(document, "RESTORED"));
        return MarkdownDocumentDtos.Response.from(document);
    }

    private MarkdownDocumentDtos.Response saveSnapshot(MarkdownDocumentEntity document, String action) {
        var saved = documents.saveAndFlush(document);
        revisions.save(new MarkdownRevisionEntity(saved, action));
        return MarkdownDocumentDtos.Response.from(saved);
    }

    private MarkdownRevisionEntity findRevision(String ownerId, String id, String revisionId) {
        return revisions.findByIdAndDocumentIdAndOwnerId(revisionId, id, ownerId)
                .orElseThrow(() -> new NotFoundException("历史版本", revisionId));
    }

    private void checkVersion(MarkdownDocumentEntity document, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new BadRequestException("expectedVersion 不能为空，请先读取文章的最新版本。");
        }
        if (expectedVersion != document.getVersion()) {
            throw new ConflictException("云端文章已有新版本。你的本地草稿仍然保留，请重新打开文章比较后再保存。");
        }
    }

    private MarkdownDocumentEntity findForUpdate(String ownerId, String id, boolean trashed) {
        var document = documents.findForUpdate(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Markdown document", id));
        if ((document.getDeletedAt() != null) != trashed) {
            throw new ConflictException(trashed ? "文章不在回收站中，请刷新列表。" : "文章已在回收站中，请先恢复文章。");
        }
        return document;
    }

    @Transactional
    public MarkdownDocumentDtos.ImportResponse importDocuments(
            String ownerId,
            MarkdownDocumentDtos.ImportRequest request
    ) {
        long totalLength = request.documents().stream().mapToLong(item -> item.content().length()).sum();
        if (totalLength > MAX_IMPORT_TOTAL_LENGTH) {
            throw new BadRequestException("Markdown import exceeds the 5,000,000 character total limit");
        }

        var imported = new ArrayList<MarkdownDocumentEntity>(request.documents().size());
        for (var item : request.documents()) {
            var fileName = normalizeFileName(item.fileName());
            var title = item.title() == null || item.title().isBlank()
                    ? deriveTitle(item.content(), fileName)
                    : normalizeRequiredTitle(item.title());
            imported.add(new MarkdownDocumentEntity(
                    ownerId,
                    domains.validateSelection(ownerId, item.domainId()),
                    title,
                    fileName,
                    normalizeContent(item.content()),
                    Boolean.TRUE.equals(item.favorite())));
        }

        var saved = documents.saveAllAndFlush(imported);
        revisions.saveAll(saved.stream().map(document -> new MarkdownRevisionEntity(document, "IMPORTED")).toList());
        return new MarkdownDocumentDtos.ImportResponse(
                saved.size(), saved.stream().map(MarkdownDocumentDtos.Response::from).toList());
    }

    @Transactional
    public MarkdownDocumentDtos.ImportResponse importFiles(
            String ownerId,
            List<MultipartFile> files,
            String domainId
    ) {
        if (files == null || files.isEmpty()) throw new BadRequestException("请选择 Markdown 文件或 ZIP 压缩包");
        if (files.size() > MarkdownDocumentDtos.MAX_IMPORT_DOCUMENTS) {
            throw new BadRequestException("一次最多选择 50 个 Markdown 文件或 ZIP 压缩包");
        }
        var selectedDomain = domains.validateSelection(ownerId, domainId);
        var pending = new ArrayList<PendingImport>();
        for (var file : files) {
            if (file == null || file.isEmpty()) throw new BadRequestException("导入文件不能为空");
            var name = originalBaseName(file.getOriginalFilename());
            if (isMarkdownFile(name)) {
                if (file.getSize() > MAX_IMPORT_MARKDOWN_BYTES) {
                    throw new BadRequestException(name + " 超过 1 MB Markdown 文件限制");
                }
                pending.add(new PendingImport(name, decodeUtf8(readMultipart(file, MAX_IMPORT_MARKDOWN_BYTES), name)));
            } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                if (file.getSize() > MAX_IMPORT_ARCHIVE_BYTES) {
                    throw new BadRequestException(name + " 超过 100 MB ZIP 限制");
                }
                pending.addAll(readArchive(ownerId, file, name));
            } else {
                throw new BadRequestException(name + "：仅支持 .md、.markdown、.mdown 或 .zip");
            }
            if (pending.size() > MarkdownDocumentDtos.MAX_IMPORT_DOCUMENTS) {
                throw new BadRequestException("一次最多导入 50 篇 Markdown 文章");
            }
        }
        if (pending.isEmpty()) throw new BadRequestException("压缩包中没有可导入的 Markdown 文件");

        long totalLength = pending.stream().mapToLong(item -> item.content().length()).sum();
        if (totalLength > MAX_IMPORT_TOTAL_LENGTH) {
            throw new BadRequestException("Markdown 正文总量不能超过 5,000,000 个字符");
        }
        var imported = new ArrayList<MarkdownDocumentEntity>(pending.size());
        for (var item : pending) {
            var fileName = normalizeFileName(item.fileName());
            var content = normalizeContent(item.content());
            if (content.length() > MarkdownDocumentDtos.MAX_CONTENT_LENGTH) {
                throw new BadRequestException(item.fileName() + " 正文不能超过 1,000,000 个字符");
            }
            imported.add(new MarkdownDocumentEntity(
                    ownerId, selectedDomain, deriveTitle(content, fileName), fileName, content, false));
        }
        var saved = documents.saveAllAndFlush(imported);
        revisions.saveAll(saved.stream().map(document -> new MarkdownRevisionEntity(document, "IMPORTED")).toList());
        return new MarkdownDocumentDtos.ImportResponse(
                saved.size(), saved.stream().map(MarkdownDocumentDtos.Response::from).toList());
    }

    @Transactional
    public List<MarkdownDocumentDtos.Response> moveToDomain(
            String ownerId,
            MarkdownDocumentDtos.BulkDomainRequest request
    ) {
        var domainId = domains.validateSelection(ownerId, request.domainId());
        var uniqueIds = request.documents().stream().map(item -> item.id().trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.size() != request.documents().size()) {
            throw new BadRequestException("批量调整中包含重复文章");
        }
        var moved = new ArrayList<MarkdownDocumentEntity>(request.documents().size());
        var changed = new ArrayList<MarkdownDocumentEntity>();
        for (var item : request.documents()) {
            var document = findForUpdate(ownerId, item.id().trim().toLowerCase(Locale.ROOT), false);
            checkVersion(document, item.expectedVersion());
            if (!Objects.equals(document.getDomainId(), domainId)) {
                document.moveToDomain(domainId);
                changed.add(document);
            }
            moved.add(document);
        }
        if (!changed.isEmpty()) {
            documents.saveAllAndFlush(changed);
            revisions.saveAll(changed.stream().map(document -> new MarkdownRevisionEntity(document, "UPDATED")).toList());
        }
        return moved.stream().map(MarkdownDocumentDtos.Response::from).toList();
    }

    private List<PendingImport> readArchive(String ownerId, MultipartFile file, String archiveName) {
        var entries = new LinkedHashMap<String, byte[]>();
        long expandedBytes = 0;
        int entryCount = 0;
        try (var zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; zip.closeEntry()) {
                if (entry.isDirectory()) continue;
                if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new BadRequestException(archiveName + " 包含的文件数量超过 500 个");
                }
                var path = normalizeArchivePath(entry.getName());
                if (path.startsWith("__MACOSX/") || path.endsWith("/.DS_Store") || path.equals(".DS_Store")) continue;
                int limit = isMarkdownFile(path) ? MAX_IMPORT_MARKDOWN_BYTES : MarkdownImageService.MAX_IMAGE_BYTES;
                var bytes = zip.readNBytes(limit + 1);
                if (bytes.length > limit) {
                    throw new BadRequestException(path + (isMarkdownFile(path)
                            ? " 超过 1 MB Markdown 文件限制" : " 超过 20 MB 图片限制"));
                }
                expandedBytes += bytes.length;
                if (expandedBytes > MAX_IMPORT_EXPANDED_BYTES) {
                    throw new BadRequestException(archiveName + " 解压后超过 120 MB 安全限制");
                }
                if (entries.putIfAbsent(path, bytes) != null) {
                    throw new BadRequestException(archiveName + " 包含重复路径：" + path);
                }
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BadRequestException(archiveName + " 不是有效的 ZIP 压缩包");
        }

        var uploaded = new LinkedHashMap<String, MarkdownImageService.UploadResponse>();
        var pending = new ArrayList<PendingImport>();
        for (var entry : entries.entrySet()) {
            if (!isMarkdownFile(entry.getKey())) continue;
            var content = decodeUtf8(entry.getValue(), entry.getKey());
            var rewritten = rewriteArchiveImages(ownerId, entry.getKey(), content, entries, uploaded);
            pending.add(new PendingImport(originalBaseName(entry.getKey()), rewritten));
        }
        return pending;
    }

    private String rewriteArchiveImages(
            String ownerId,
            String markdownPath,
            String content,
            Map<String, byte[]> entries,
            Map<String, MarkdownImageService.UploadResponse> uploaded
    ) {
        var matcher = MARKDOWN_IMAGE_LINK.matcher(content);
        var output = new StringBuffer();
        while (matcher.find()) {
            var target = matcher.group(2);
            if (isRemoteOrManagedTarget(target)) continue;
            var assetPath = resolveArchiveReference(markdownPath, target);
            var bytes = entries.get(assetPath);
            if (bytes == null) continue;
            var result = uploaded.computeIfAbsent(assetPath,
                    ignored -> imageService.upload(ownerId, originalBaseName(assetPath), bytes));
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(
                    matcher.group(1) + result.url() + matcher.group(3)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private boolean isRemoteOrManagedTarget(String target) {
        var lower = target.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")
                || lower.startsWith("//") || lower.startsWith("/") || lower.startsWith("#");
    }

    private String resolveArchiveReference(String markdownPath, String target) {
        var slash = markdownPath.lastIndexOf('/');
        var base = slash < 0 ? "" : markdownPath.substring(0, slash + 1);
        return normalizeArchivePath(base + target.replace('\\', '/'));
    }

    private String normalizeArchivePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) throw new BadRequestException("ZIP 中存在空文件路径");
        var normalized = Normalizer.normalize(rawPath, Normalizer.Form.NFC).replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[a-zA-Z]:.*")) {
            throw new BadRequestException("ZIP 中包含不安全的绝对路径");
        }
        var segments = new ArrayList<String>();
        for (var segment : normalized.split("/")) {
            if (segment.isBlank() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (segments.isEmpty()) throw new BadRequestException("ZIP 中包含越界路径");
                segments.removeLast();
            } else {
                if (segment.indexOf('\0') >= 0) throw new BadRequestException("ZIP 中包含无效路径");
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) throw new BadRequestException("ZIP 中包含无效文件路径");
        return String.join("/", segments);
    }

    private byte[] readMultipart(MultipartFile file, int maxBytes) {
        try (var input = file.getInputStream()) {
            var bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new BadRequestException("导入文件超过大小限制");
            return bytes;
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BadRequestException("无法读取导入文件");
        }
    }

    private String decodeUtf8(byte[] bytes, String fileName) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new BadRequestException(fileName + " 必须使用 UTF-8 编码");
        }
    }

    private boolean isMarkdownFile(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown");
    }

    private String originalBaseName(String name) {
        if (name == null || name.isBlank()) return "untitled.md";
        var normalized = name.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    public byte[] exportDocuments(String ownerId, MarkdownDocumentDtos.ExportRequest request) {
        var requestedIds = request.ids().stream()
                .map(id -> id.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<MarkdownDocumentEntity> selected;
        if (requestedIds.isEmpty()) {
            var documentCount = documents.countByOwnerIdAndDeletedAtIsNull(ownerId);
            if (documentCount == 0) {
                throw new BadRequestException("There are no Markdown documents to export");
            }
            if (documentCount > MarkdownDocumentDtos.MAX_EXPORT_DOCUMENTS) {
                throw new BadRequestException("At most 100 Markdown documents can be exported at once");
            }
            ensureExportSize(documents.sumContentOctetsByOwnerId(ownerId));
            selected = documents.findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc(ownerId);
        } else {
            var documentCount = documents.countByOwnerIdAndIdInAndDeletedAtIsNull(ownerId, requestedIds);
            if (documentCount != requestedIds.size()) {
                throw new NotFoundException("Markdown document", "one or more requested IDs");
            }
            ensureExportSize(documents.sumContentOctetsByOwnerIdAndIdIn(ownerId, requestedIds));
            selected = documents.findAllByOwnerIdAndIdInAndDeletedAtIsNull(ownerId, requestedIds);
            if (selected.size() != requestedIds.size()) {
                throw new NotFoundException("Markdown document", "one or more requested IDs");
            }
            var byId = new LinkedHashMap<String, MarkdownDocumentEntity>();
            selected.forEach(document -> byId.put(document.getId().toLowerCase(Locale.ROOT), document));
            selected = requestedIds.stream().map(byId::get).toList();
        }

        if (selected.isEmpty()) {
            throw new BadRequestException("There are no Markdown documents to export");
        }
        if (selected.size() > MarkdownDocumentDtos.MAX_EXPORT_DOCUMENTS) {
            throw new BadRequestException("At most 100 Markdown documents can be exported at once");
        }

        var loadedBytes = selected.stream()
                .mapToLong(document -> document.getContent().getBytes(StandardCharsets.UTF_8).length)
                .sum();
        var imageIds = new LinkedHashSet<String>();
        for (var document : selected) collectManagedImageIds(document.getContent(), imageIds);
        var assets = imageService.loadForExport(ownerId, imageIds);
        loadedBytes += assets.values().stream().mapToLong(MarkdownImageService.ExportAsset::size).sum();
        ensureExportSize(loadedBytes);
        return createZip(selected, assets);
    }

    private void ensureExportSize(long uncompressedBytes) {
        if (uncompressedBytes > MAX_EXPORT_UNCOMPRESSED_BYTES) {
            throw new BadRequestException("Markdown 与图片导出总量不能超过 100 MB");
        }
    }

    private byte[] createZip(
            List<MarkdownDocumentEntity> selected,
            Map<String, MarkdownImageService.ExportAsset> assets
    ) {
        var output = new ByteArrayOutputStream();
        var usedNames = new HashSet<String>();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var document : selected) {
                var entryName = uniqueFileName(normalizeFileName(document.getFileName()), usedNames);
                var entry = new ZipEntry(entryName);
                if (document.getUpdatedAt() != null) {
                    entry.setTime(document.getUpdatedAt().toEpochMilli());
                }
                zip.putNextEntry(entry);
                zip.write(rewriteExportImages(document.getContent(), assets).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (var asset : assets.values()) {
                var entry = new ZipEntry("assets/" + asset.fileName());
                zip.putNextEntry(entry);
                zip.write(asset.bytes());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Markdown export", exception);
        }
        return output.toByteArray();
    }

    private void collectManagedImageIds(String content, Set<String> imageIds) {
        var matcher = MARKDOWN_IMAGE_LINK.matcher(content);
        while (matcher.find()) {
            var managed = MANAGED_IMAGE_TARGET.matcher(matcher.group(2));
            if (managed.matches()) imageIds.add(managed.group(1).toLowerCase(Locale.ROOT));
        }
    }

    private String rewriteExportImages(
            String content,
            Map<String, MarkdownImageService.ExportAsset> assets
    ) {
        var matcher = MARKDOWN_IMAGE_LINK.matcher(content);
        var output = new StringBuffer();
        while (matcher.find()) {
            var managed = MANAGED_IMAGE_TARGET.matcher(matcher.group(2));
            if (!managed.matches()) continue;
            var asset = assets.get(managed.group(1).toLowerCase(Locale.ROOT));
            if (asset == null) continue;
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(
                    matcher.group(1) + "assets/" + asset.fileName() + matcher.group(3)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private MarkdownDocumentEntity find(String ownerId, String id) {
        return documents.findByIdAndOwnerId(id, ownerId)
                .filter(document -> document.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Markdown document", id));
    }

    private String normalizeContent(String content) {
        String normalized;
        if (!content.isEmpty() && content.charAt(0) == '\ufeff') {
            normalized = content.substring(1);
        } else {
            normalized = content;
        }
        if (normalized.isBlank()) {
            throw new BadRequestException("Markdown content cannot be empty");
        }
        return normalized;
    }

    private String normalizeRequiredTitle(String title) {
        var normalized = UNSAFE_TITLE_CHARACTERS.matcher(title).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException("Markdown title cannot be empty");
        }
        return truncate(normalized, 200);
    }

    private String deriveTitle(String content, String fileName) {
        for (var line : content.lines().toList()) {
            var matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                var heading = matcher.group(1)
                        .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                        .replaceAll("[*_`~]", "");
                if (!heading.isBlank()) return normalizeRequiredTitle(heading);
            }
        }
        var stem = stripMarkdownExtension(fileName);
        return normalizeRequiredTitle(stem.isBlank() ? "Untitled" : stem);
    }

    private String normalizeFileName(String input) {
        var normalizedPath = Normalizer.normalize(input, Normalizer.Form.NFC).replace('\\', '/');
        var lastSlash = normalizedPath.lastIndexOf('/');
        var baseName = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        baseName = UNSAFE_FILE_CHARACTERS.matcher(baseName).replaceAll("_").trim();
        baseName = baseName.replaceAll("[. ]+$", "");
        if (baseName.isBlank() || baseName.equals(".") || baseName.equals("..")) {
            baseName = "untitled";
        }

        var extension = markdownExtension(baseName);
        var stem = extension.isEmpty() ? baseName : baseName.substring(0, baseName.length() - extension.length());
        if (WINDOWS_RESERVED_NAMES.contains(stem.toUpperCase(Locale.ROOT))) {
            stem = "_" + stem;
        }
        extension = extension.isEmpty() ? ".md" : extension;
        stem = truncate(stem, 255 - extension.length());
        return stem + extension;
    }

    private String uniqueFileName(String fileName, Set<String> usedNames) {
        if (usedNames.add(normalizedFileNameKey(fileName))) return fileName;
        var extension = markdownExtension(fileName);
        var stem = fileName.substring(0, fileName.length() - extension.length());
        for (int suffix = 2; ; suffix++) {
            var suffixText = " (" + suffix + ")";
            var candidate = truncate(stem, 255 - extension.length() - suffixText.length()) + suffixText + extension;
            if (usedNames.add(normalizedFileNameKey(candidate))) return candidate;
        }
    }

    private String normalizedFileNameKey(String fileName) {
        return Normalizer.normalize(fileName, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private String stripMarkdownExtension(String fileName) {
        var extension = markdownExtension(fileName);
        return extension.isEmpty() ? fileName : fileName.substring(0, fileName.length() - extension.length());
    }

    private String markdownExtension(String fileName) {
        var lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".markdown")) return fileName.substring(fileName.length() - 9);
        if (lower.endsWith(".md")) return fileName.substring(fileName.length() - 3);
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        var end = maxLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

}
