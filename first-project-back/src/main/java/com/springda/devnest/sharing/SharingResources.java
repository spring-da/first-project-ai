package com.springda.devnest.sharing;

import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.image.MarkdownImageService;
import com.springda.devnest.image.ManagedMarkdownImages;
import com.springda.devnest.markdown.MarkdownDocumentRepository;
import com.springda.devnest.snippet.SnippetRepository;
import com.springda.devnest.flowchart.FlowchartRepository;
import com.springda.devnest.user.UserRepository;
import org.springframework.stereotype.Component;
import java.util.List;

/** Resolves saved content at every read; sharing tables never contain body snapshots. */
@Component
public class SharingResources {
    private final MarkdownDocumentRepository documents;
    private final SnippetRepository snippets;
    private final FlowchartRepository flowcharts;
    private final UserRepository users;
    private final MarkdownImageService images;
    private final tools.jackson.databind.ObjectMapper json;
    public SharingResources(MarkdownDocumentRepository documents, SnippetRepository snippets,
                            FlowchartRepository flowcharts, UserRepository users, MarkdownImageService images, tools.jackson.databind.ObjectMapper json) {
        this.documents = documents; this.snippets = snippets; this.flowcharts = flowcharts; this.users = users; this.images = images; this.json = json;
    }
    public record Saved(long generation, SharingDtos.SharedContent content) {}
    public Saved resolve(String ownerId, ResourceType type, String id, String shareItemId) {
        return switch (type) {
            case MARKDOWN -> {
                var item = documents.findByIdAndOwnerId(id, ownerId).filter(d -> d.getDeletedAt() == null).orElseThrow(SharingResources::unavailable);
                yield new Saved(item.getSharingGeneration(), new SharingDtos.SharedContent(shareItemId, type, item.getTitle(), item.getContent(), null, null, List.of(), item.getUpdatedAt(), null));
            }
            case SNIPPET -> {
                var item = snippets.findByIdAndOwnerId(id, ownerId).filter(d -> d.getDeletedAt() == null).orElseThrow(SharingResources::unavailable);
                yield new Saved(item.getSharingGeneration(), new SharingDtos.SharedContent(shareItemId, type, item.getTitle(), item.getCode(), item.getLanguage(), null, List.of(), item.getUpdatedAt(), null));
            }
            case FLOWCHART -> {
                var item = flowcharts.findByIdAndOwnerId(id, ownerId).filter(d -> d.getDeletedAt() == null).orElseThrow(SharingResources::unavailable);
                yield new Saved(item.getSharingGeneration(), new SharingDtos.SharedContent(shareItemId, type, item.getTitle(), item.getSearchText(), null, null, List.of(), item.getUpdatedAt(), json.readTree(item.getDiagram())));
            }
        };
    }
    public void requireEnabled(String ownerId) {
        if (users.findById(ownerId).filter(u -> u.isEnabled()).isEmpty()) throw unavailable();
    }
    public SharingDtos.SharedContent read(String ownerId, ResourceType type, String resourceId, long generation, String itemId) {
        requireEnabled(ownerId);
        var saved = resolve(ownerId, type, resourceId, itemId);
        if (saved.generation() != generation) throw unavailable();
        return saved.content();
    }
    public MarkdownImageService.ImageContent image(String ownerId, SharingDtos.SharedContent content, String imageId) {
        if (content.resourceType() != ResourceType.MARKDOWN) throw unavailable();
        var referencedId = ManagedMarkdownImages.referencedId(content.content(), imageId);
        if (referencedId == null) throw unavailable();
        return images.read(ownerId, referencedId);
    }
    static NotFoundException unavailable() { return new NotFoundException("分享内容不存在、已失效或已被撤销"); }
}
