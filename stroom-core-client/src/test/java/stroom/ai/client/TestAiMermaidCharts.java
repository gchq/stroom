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
                .startsWith("<p>Alice's morning:</p>\n<div class=\"ai-gantt\"><svg ")
                .contains(">R&amp;D (times in UTC)</text>")
                .endsWith("</svg></div>\n<p>Folded: nothing.</p>")
                .doesNotContain("<pre>", "language-mermaid");
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

        assertThat(drawn).containsSubsequence("<div class=\"ai-gantt\">", "<p>and</p>", bad,
                "<pre class=\"x\">text</pre>", "<div class=\"ai-gantt\">");
        assertThat(drawn.split("<svg ", -1)).hasSize(3);
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
