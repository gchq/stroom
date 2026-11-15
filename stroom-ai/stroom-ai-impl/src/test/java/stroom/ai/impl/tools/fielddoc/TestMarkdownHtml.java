/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.tools.fielddoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Generated documentation keeps HTML out: Stroom renders a Documentation doc with showdown, unsanitised, so a tag
/// outside code is markup. Values from the data go in code or are escaped, and a whole document's tags outside code
/// are escaped, the tools' own line breaks, hidden blocks and autolinks kept.
class TestMarkdownHtml {

    // As the Azure audit sample's bodies hold them (stroom-app's aggregated/azure-audit test resources).
    private static final String MAIL_BODY = "<html><head><meta charset=\"utf-8\"></head><body dir=\"ltr\"><p>Morning "
                                            + "all,</p><p>Please find the figures attached</p></body></html>";
    private static final String SUBJECT = "Markup in text: <xsl:value-of select=\"'injected'\"/> <![CDATA[ inner ]]> "
                                          + "]]> <!--";
    private static final String TEAMS = "<div><p>See the diff below – the regex was `^(?&lt;user&gt;[^@]+)@contoso"
                                        + "\\.example$` before the fix.</p></div>";
    private static final String SHARING = "<PermissionsGranted>Full Control</PermissionsGranted><Type>Edit</Type>";
    private static final List<String> HOSTILE = List.of(MAIL_BODY, SUBJECT, TEAMS, SHARING,
            "<f8ce0925-1d31-cdfa-43a9-370a3d1cfa00@contoso.example>", "<img src=x onerror=alert(1)>");
    // A tag's start outside code, as a test of the rendered Markdown sees it.
    private static final Pattern TAG = Pattern.compile("<[A-Za-z/!?]");

    @Test
    void textIsEscapedAndCodeIsOneLineWithoutBackticks() {
        assertThat(MarkdownHtml.text("a < b & <b>c</b>")).isEqualTo("a &lt; b &amp; &lt;b&gt;c&lt;/b&gt;");
        assertThat(MarkdownHtml.text(null)).isEmpty();
        assertThat(MarkdownHtml.code("a\r\n`b`  <p>")).isEqualTo("`a 'b' <p>`");
        // Every hostile value is code from end to end: no backtick inside to end it early.
        HOSTILE.forEach(value -> assertThat(outsideCode(MarkdownHtml.code(value))).isEmpty());
        HOSTILE.forEach(value -> assertThat(TAG.matcher(MarkdownHtml.text(value)).find()).isFalse());
    }

    @Test
    void tagsOutsideCodeAreEscapedAndCodeIsLeftAsItIs() {
        final String markdown = "## Purpose\n\nMail bodies like " + MAIL_BODY + " and " + SUBJECT + "\n\n"
                                + "Inline `<b>kept</b>` and ``a ` <i>b</i>``.\n\n"
                                + "```xml\n<Event><Data Name=\"x\"/></Event>\n```\n\n"
                                + "~~~\n<script>still code</script>\n~~~\n\n"
                                + "<script>alert(1)</script> and a < b, 3<5.\n";

        final String escaped = MarkdownHtml.escapeTags(markdown);

        assertThat(escaped)
                .contains("Mail bodies like &lt;html>&lt;head>&lt;meta charset=\"utf-8\">")
                .contains("&lt;xsl:value-of select=\"'injected'\"/> &lt;![CDATA[ inner ]]> ]]> &lt;!--")
                .contains("Inline `<b>kept</b>` and ``a ` <i>b</i>``.")
                .contains("```xml\n<Event><Data Name=\"x\"/></Event>\n```")
                .contains("~~~\n<script>still code</script>\n~~~")
                .contains("&lt;script>alert(1)&lt;/script> and a < b, 3<5.");
        assertThat(TAG.matcher(outsideCode(escaped)).find()).as(outsideCode(escaped)).isFalse();
        // Applied again, nothing changes.
        assertThat(MarkdownHtml.escapeTags(escaped)).isEqualTo(escaped);
    }

    @Test
    void theToolsOwnLineBreaksHiddenBlocksAndAutolinksAreKept() {
        final String markdown = "| a | one<br>two<br/>three |\n\n"
                                + "See <https://stroom.example/docs> and <mailto:ops@example.org>.\n\n"
                                + "<!-- stroom-mcp field descriptions\n{\"Body\": \"<p>held</p>\"}\n-->\n\n"
                                + "<!-- stroom-mcp field-mapping 0123456789abcdef --> <b>after</b>\n"
                                + "<!-- a user's comment -->\n";

        final String escaped = MarkdownHtml.escapeTags(markdown);

        assertThat(escaped)
                .contains("| a | one<br>two<br/>three |")
                .contains("See <https://stroom.example/docs> and <mailto:ops@example.org>.")
                .contains("<!-- stroom-mcp field descriptions\n{\"Body\": \"<p>held</p>\"}\n-->")
                .contains("<!-- stroom-mcp field-mapping 0123456789abcdef --> &lt;b>after&lt;/b>")
                // Only the tools' own comments are kept: another is shown, not hidden.
                .contains("&lt;!-- a user's comment -->");
        // A hidden block closed early by the data ends where a browser ends it: what follows is escaped.
        assertThat(MarkdownHtml.escapeTags("<!-- stroom-mcp x --> <img src=x> -->"))
                .isEqualTo("<!-- stroom-mcp x --> &lt;img src=x> -->");
    }

    @Test
    void sampleValuesAreShownAsCodeNotMarkup() {
        final String shown = FieldDocs.shown(HOSTILE, true);

        assertThat(shown.lines()).hasSize(FieldDocs.VALUES_SHOWN + 1);
        shown.lines().limit(FieldDocs.VALUES_SHOWN).forEach(line -> assertThat(line).startsWith("`").endsWith("`"));
        assertThat(TAG.matcher(outsideCode(shown)).find()).as(shown).isFalse();
        assertThat(TAG.matcher(FieldDocs.shown(HOSTILE, false)).find()).isFalse();
    }

    /// @param markdown Markdown.
    /// @return It without its fenced code blocks, code spans, the tools' hidden blocks and `<br>`s: what showdown
    /// would read as markup.
    static String outsideCode(final String markdown) {
        return markdown.replaceAll("(?ms)^ {0,3}(`{3,}|~{3,}).*?^ {0,3}\\1[^\\n]*$", "")
                .replaceAll("(`+)[^`]*?(?:`(?!\\1)[^`]*?)*?\\1", "")
                .replaceAll("(?s)<!-- stroom-mcp.*?-->", "")
                .replaceAll("(?i)<br\\s*/?>", "")
                .replaceAll("<(?:https?|mailto):[^\\s<>]*>", "");
    }
}
