package com.springda.devnest.image;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.parser.Parser;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

/** Resolves actual Markdown image nodes, including titles and reference-style images. */
public final class ManagedMarkdownImages {
    private static final Parser PARSER = Parser.builder().build();
    private static final Pattern TARGET = Pattern.compile(
            "^/api/v1/markdown-images/([a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})$", Pattern.CASE_INSENSITIVE);
    private ManagedMarkdownImages() {}
    public static String referencedId(String content, String requestedId) {
        if (content == null || requestedId == null) return null;
        var referenced = new HashSet<String>();
        PARSER.parse(content).accept(new AbstractVisitor() {
            @Override public void visit(Image image) {
                var target = TARGET.matcher(image.getDestination());
                if (target.matches()) referenced.add(target.group(1).toLowerCase(Locale.ROOT));
            }
        });
        var normalized = requestedId.toLowerCase(Locale.ROOT);
        return referenced.contains(normalized) ? normalized : null;
    }
}
