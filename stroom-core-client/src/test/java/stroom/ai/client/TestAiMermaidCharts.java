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

package stroom.ai.client;

import stroom.ai.shared.MermaidGantt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// An answer's Mermaid code blocks, as the shared Markdown converter renders them: a gantt chart of the subset
/// drawn as the chat's own SVG, anything else left as code.
class TestAiMermaidCharts {

    // As showdown renders a ```mermaid block, and the sanitiser keeps it: its text escaped, its classes kept.
    private static final String GANTT_BLOCK = "<pre><code class=\"mermaid language-mermaid\">gantt\n"
                                              + "    title R&amp;D (times in UTC)\n"
                                              + "    dateFormat YYYY-MM-DD HH:mm:ss\n"
                                              + "    section host-1\n"
                                              + "    Chrome :2026-11-23 09:00:00, 2026-11-23 09:30:00\n"
                                              + "</code></pre>";

    @Test
    void aGanttBlockOfTheSubsetIsDrawnAndTheRestOfTheAnswerKept() {
        final String html = "<p>Alice's morning:</p>\n" + GANTT_BLOCK + "\n<p>Folded: nothing.</p>";

        final String drawn = AiMermaidCharts.drawCharts(html);

        assertThat(drawn)
                .startsWith("<p>Alice's morning:</p>\n<div class=\"ai-gantt\" data-gantt=\"gantt&#10;")
                .contains(">R&amp;D (times in UTC)</text>")
                .contains("<div class=\"ai-gantt-plot\"><svg ")
                .endsWith("</svg></div></div>\n<p>Folded: nothing.</p>")
                .doesNotContain("<pre>", "language-mermaid");
    }

    @Test
    void aChartKeepsItsTextEscapedAndHasItsButtons() {
        final String text = "gantt\n    title R&D (times in UTC)\n    t :2026-11-23, 1d\n";

        final String chart = AiMermaidCharts.chart(text);

        // The text, escaped, its line breaks as references, read back by the browser as written.
        assertThat(chart).contains("data-gantt=\"gantt&#10;    title R&amp;D (times in UTC)&#10;    t :2026-11-23, "
                                   + "1d&#10;\"")
                .doesNotContain("data-gantt-view", "data-gantt-width");
        // Reset zoom hidden until the chart is zoomed; Maximise in the chat.
        assertThat(chart).contains("<button type=\"button\" class=\"ai-gantt-button ai-gantt-reset\" "
                                   + "title=\"Show the whole period again\" aria-label=\"Show the whole period again\" "
                                   + "hidden>")
                .contains("<span>Reset zoom</span>")
                .contains("class=\"ai-gantt-button ai-gantt-maximise\"")
                .contains("<span>Maximise</span>");
        assertThat(AiMermaidCharts.unescape("gantt&#10;    title R&amp;D")).isEqualTo("gantt\n    title R&D");
    }

    @Test
    void aChartInTheDialogIsDrawnAtItsWidthZoomedAndWithoutMaximise() {
        final String text = "gantt\n    dateFormat YYYY-MM-DD HH:mm:ss\n"
                            + "    t :2026-11-23 09:00:00, 2026-11-23 12:00:00\n";
        final long nine = MermaidGantt.wallClock(2026, 11, 23, 9, 0, 0, 0);
        final long[] view = {nine + 3_600_000L, nine + 7_200_000L};

        final String chart = AiMermaidCharts.chart(text, AiMermaidCharts.parse(text), view, 1000, false);

        assertThat(chart).contains("data-gantt-view=\"" + view[0] + "," + view[1] + "\"")
                .contains("data-gantt-width=\"1000\"")
                .contains("viewBox=\"0 0 1000 ")
                .contains("class=\"ai-gantt-button ai-gantt-reset\" title=\"Show the whole period again\" "
                          + "aria-label=\"Show the whole period again\">")
                .doesNotContain("ai-gantt-maximise");
        assertThat(AiMermaidCharts.parse("gantt\n    click t call alert(1)")).isNull();
        assertThat(AiMermaidCharts.parse(null)).isNull();
    }

    @Test
    void aBlockOutsideTheSubsetOrOfAnotherLanguageStaysAsCode() {
        final String injected = "<pre><code class=\"mermaid language-mermaid\">gantt\n"
                                + "    click t1 call alert(1)\n</code></pre>";
        final String pie = "<pre><code class=\"mermaid language-mermaid\">pie\n  \"a\" : 1\n</code></pre>";
        final String init = "<pre><code class=\"language-mermaid\">%%{init: {}}%%\ngantt\n</code></pre>";
        final String javaBlock = "<pre><code class=\"java language-java\">gantt\n</code></pre>";
        final String plain = "<pre><code>gantt\n    t :2026-11-23, 1d\n</code></pre>";

        for (final String html : new String[]{injected, pie, init, javaBlock, plain}) {
            assertThat(AiMermaidCharts.drawCharts(html)).isEqualTo(html);
        }
    }

    @Test
    void aBlockWithMarkupInItIsntOneTheConverterMadeSoStaysAsItIs() {
        final String html = "<pre><code class=\"language-mermaid\">gantt\n<b>t</b> :2026-11-23, 1d\n</code></pre>";

        assertThat(AiMermaidCharts.drawCharts(html)).isEqualTo(html);
    }

    @Test
    void severalBlocksAreEachDrawnOrLeft() {
        final String bad = "<pre><code class=\"language-mermaid\">gantt\n    excludes weekends\n</code></pre>";
        final String html = GANTT_BLOCK + "<p>and</p>" + bad + "<pre class=\"x\">text</pre>" + GANTT_BLOCK;

        final String drawn = AiMermaidCharts.drawCharts(html);

        assertThat(drawn).containsSubsequence("<div class=\"ai-gantt\" ", "<p>and</p>", bad,
                "<pre class=\"x\">text</pre>", "<div class=\"ai-gantt\" ");
        assertThat(drawn.split("class=\"ai-gantt-svg\"", -1)).hasSize(3);
    }

    @Test
    void characterReferencesAreReadAsTheTextTheyShow() {
        assertThat(AiMermaidCharts.unescape("a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39; &#x23; &nbsp;f"))
                .isEqualTo("a & b <c> \"d\" 'e' #  f");
        // Not references: left as they are.
        assertThat(AiMermaidCharts.unescape("&unknown; & &#xZZ; &")).isEqualTo("&unknown; & &#xZZ; &");
        // A reference to a syntax character is read, so the parser refuses it as it would the character.
        assertThat(AiMermaidCharts.chart("gantt\n" + AiMermaidCharts.unescape("t &#35;35; :2026-11-23, 1d")))
                .isNull();
        assertThat(AiMermaidCharts.drawCharts(null)).isNull();
    }
}
