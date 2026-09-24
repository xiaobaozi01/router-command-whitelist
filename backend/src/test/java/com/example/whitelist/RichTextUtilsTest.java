package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.util.RichTextUtils;
import org.junit.jupiter.api.Test;

class RichTextUtilsTest {
    @Test
    void keepsOnlySupportedFormattingTagsAndRemovesAttributes() {
        String sanitized = RichTextUtils.sanitizeFormatting(
                "<p class='x'><b onclick='bad()'>display</b> <i>IP</i><script>alert(1)</script></p>");

        assertThat(sanitized)
                .isEqualTo("<p><strong>display</strong> <em>IP</em>alert(1)</p>")
                .doesNotContain("onclick", "script", "class");
    }

    @Test
    void normalizesLegacyStrikeTag() {
        assertThat(RichTextUtils.sanitizeFormatting("old <strike>command</strike>"))
                .isEqualTo("<p>old <s>command</s></p>");
    }

    @Test
    void extractsAndRemovesUnsupportedStrikethroughContent() {
        String html = "<p><strong>display</strong> <s><strong>ipv6</strong></s> <em>interface</em> <s>brief</s></p>";

        assertThat(RichTextUtils.toPlainTextWithoutStrikethrough(html))
                .isEqualTo("display interface");
        assertThat(RichTextUtils.withoutStrikethrough(html))
                .isEqualTo("<p><strong>display</strong>  <em>interface</em> </p>");
        assertThat(RichTextUtils.strikethroughTexts(html))
                .containsExactly("ipv6", "brief");
    }
}
