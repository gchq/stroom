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

package stroom.pipeline.stepping.client.presenter;

import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCodeDiffCardRenderer {

    @Test
    void testSummary() {
        assertThat(CodeDiffCardRenderer.summary(LineDiff.diff("a\nb", "a\nB\nc\nd")))
                .isEqualTo("+3 −1 lines");
        assertThat(CodeDiffCardRenderer.summary(LineDiff.diff("a", "a")))
                .isEqualTo("No changes");
        assertThat(CodeDiffCardRenderer.summary(LineDiff.diff("a\nb", "a")))
                .isEqualTo("+0 −1 lines");
    }

    @Test
    void testFoldedSummary() {
        assertThat(CodeDiffCardRenderer.foldedSummary(1)).isEqualTo("1 more change");
        assertThat(CodeDiffCardRenderer.foldedSummary(4)).isEqualTo("4 more changes");
    }

    @Test
    void testRender() {
        final LineDiffResult diff = LineDiff.diff(
                "<a>\n  <b>one</b>\n</a>",
                "<a>\n  <b>two</b>\n  <c/>\n</a>");
        final String html = CodeDiffCardRenderer.render(diff).asString();

        assertThat(html).startsWith("<div class=\"codeDiffCard\">");
        assertThat(html).contains("<div class=\"codeDiffCard-summary\">+2 −1 lines</div>");
        assertThat(html).contains("<div class=\"codeDiffCard-header\">@@ -1,3 +1,4 @@</div>");
        // Text is escaped.
        assertThat(html).contains("&lt;b&gt;one&lt;/b&gt;");
        assertThat(html).doesNotContain("<b>one</b>");

        // Context, then the removed line before the added ones, then context.
        final int context = html.indexOf("codeDiffCard-line--context");
        final int removed = html.indexOf("codeDiffCard-line--removed");
        final int added = html.indexOf("codeDiffCard-line--added");
        final int lastContext = html.lastIndexOf("codeDiffCard-line--context");
        assertThat(context).isLessThan(removed);
        assertThat(removed).isLessThan(added);
        assertThat(added).isLessThan(lastContext);
        assertThat(count(html, "codeDiffCard-line--removed")).isEqualTo(1);
        assertThat(count(html, "codeDiffCard-line--added")).isEqualTo(2);

        // Removed lines show only the old line number, added only the new.
        assertThat(html).contains("<div class=\"codeDiffCard-line codeDiffCard-line--removed\">"
                                  + "<span class=\"codeDiffCard-lineNo\">2</span>"
                                  + "<span class=\"codeDiffCard-lineNo\"></span>"
                                  + "<span class=\"codeDiffCard-marker\">−</span>");
        assertThat(html).doesNotContain("codeDiffCard-more");
    }

    @Test
    void testLongDiffsAreFolded() {
        final List<String> oldLines = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            oldLines.add("line " + i);
        }
        final List<String> newLines = new ArrayList<>(oldLines);
        // Five changes, far enough apart to be separate hunks.
        for (int i = 0; i < 5; i++) {
            newLines.set(i * 20, "changed " + i);
        }
        final LineDiffResult diff = LineDiff.diff(oldLines, newLines);
        assertThat(diff.getHunks(3)).hasSize(5);

        final String html = CodeDiffCardRenderer.render(diff).asString();
        assertThat(count(html, "class=\"codeDiffCard-hunk\"")).isEqualTo(5);
        assertThat(html).contains("<details class=\"codeDiffCard-more\">"
                                  + "<summary class=\"codeDiffCard-moreSummary\">2 more changes</summary>");
        // The first three hunks come before the fold.
        final int fold = html.indexOf("<details");
        assertThat(count(html.substring(0, fold), "class=\"codeDiffCard-hunk\"")).isEqualTo(3);

        // Fewer visible hunks.
        final String oneVisible = CodeDiffCardRenderer.render(diff, 3, 1).asString();
        assertThat(oneVisible).contains("4 more changes");
    }

    private static int count(final String text, final String part) {
        int count = 0;
        int index = text.indexOf(part);
        while (index >= 0) {
            count++;
            index = text.indexOf(part, index + part.length());
        }
        return count;
    }
}
