package com.springda.devnest.image;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class ManagedMarkdownImagesTest {
    private static final String ID = "12345678-1234-1234-1234-123456789abc";
    private static final String URL = "/api/v1/markdown-images/" + ID;
    @ParameterizedTest
    @ValueSource(strings = {
            "![alt](IMAGE)", "![alt](IMAGE \"caption\")", "![alt](IMAGE 'caption')",
            "![alt](IMAGE (caption))", "![alt](<IMAGE> \"caption\")",
            "![escaped \\] label](IMAGE \"caption\")", "![alt][photo]\n\n[photo]: IMAGE \"caption\""
    })
    void recognizesActualManagedImageNodes(String markdown) {
        assertThat(ManagedMarkdownImages.referencedId(markdown.replace("IMAGE", URL), ID.toUpperCase())).isEqualTo(ID);
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "[ordinary link](IMAGE)", "`![code](IMAGE)`", "```md\n![example](IMAGE)\n```",
            "\\![escaped image](IMAGE)", "![remote](https://evil.exampleIMAGE)", "![query](IMAGE?x=1)",
            "![fragment](IMAGE#fragment)", "![suffix](IMAGEextra)",
            "![other](/unrelated \"![fake](IMAGE)\")", "<img src=\"IMAGE\">"
    })
    void doesNotGrantAccessForTextCodeOrNonManagedDestinations(String markdown) {
        assertThat(ManagedMarkdownImages.referencedId(markdown.replace("IMAGE", URL), ID)).isNull();
    }
}
