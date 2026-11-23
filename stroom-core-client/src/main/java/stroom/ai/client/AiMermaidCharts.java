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
import stroom.ai.shared.MermaidGanttParser;

/// Draws the charts in an answer: after the shared Markdown converter has turned the answer to sanitised HTML,
/// each ```` ```mermaid ```` code block it rendered (`<pre><code class="... language-mermaid">`) whose text is a
/// gantt chart of the subset ([MermaidGanttParser]) is replaced by Stroom's own SVG of it ([AiGanttChart]). A
/// block that isn't (another kind of chart, or one outside the subset) stays as code: nothing in it is ever run.
/// Only the chat does this; the converter and every other use of it are unchanged.
final class AiMermaidCharts {

    /// The class of the element a chart is drawn in.
    static final String CHART_CLASS = "ai-gantt";

    private static final String LANGUAGE_CLASS = "language-mermaid";
    private static final String PRE = "<pre";
    private static final String CODE = "<code";
    private static final String CODE_END = "</code>";
    private static final String PRE_END = "</pre>";

    private AiMermaidCharts() {
        // Static only.
    }

    /// @param html An answer as the Markdown converter gives it (sanitised).
    /// @return It with each Mermaid code block of the subset drawn as a chart, and everything else as it was.
    static String drawCharts(final String html) {
        if (html == null || !html.contains(LANGUAGE_CLASS)) {
            return html;
        }
        final StringBuilder sb = new StringBuilder();
        int from = 0;
        int at = html.indexOf(PRE, from);
        while (at >= 0) {
            final Block block = block(html, at);
            if (block != null) {
                final String chart = chart(block.text());
                if (chart != null) {
                    sb.append(html, from, at).append(chart);
                    from = block.end();
                }
            }
            at = html.indexOf(PRE, Math.max(from, at + PRE.length()));
        }
        return sb.append(html.substring(from)).toString();
    }

    /// @param text A Mermaid code block's text.
    /// @return It drawn as a chart, in an element of [#CHART_CLASS], or null if it isn't a gantt chart of the
    /// subset.
    static String chart(final String text) {
        final MermaidGantt gantt;
        try {
            gantt = MermaidGanttParser.parse(text);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        return "<div class=\"" + CHART_CLASS + "\">" + AiGanttChart.svg(gantt) + "</div>";
    }

    /// @param html Text of HTML, as an element's inner HTML gives it.
    /// @return The text it shows: its character references (`&amp;`, `&lt;`, `&#35;`, ...) read.
    static String unescape(final String html) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < html.length()) {
            final char c = html.charAt(i);
            final int semicolon = c == '&'
                    ? html.indexOf(';', i)
                    : -1;
            if (semicolon > i && semicolon - i <= 10) {
                final String reference = reference(html.substring(i + 1, semicolon));
                if (reference != null) {
                    sb.append(reference);
                    i = semicolon + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    // The Mermaid code block that starts at a <pre>, or null if it isn't one.
    private static Block block(final String html, final int at) {
        final int preTagEnd = html.indexOf('>', at);
        if (preTagEnd < 0 || !isTagName(html, at + PRE.length())) {
            return null;
        }
        final int codeStart = preTagEnd + 1;
        if (!html.startsWith(CODE, codeStart) || !isTagName(html, codeStart + CODE.length())) {
            return null;
        }
        final int codeTagEnd = html.indexOf('>', codeStart);
        if (codeTagEnd < 0 || !hasMermaidClass(html.substring(codeStart, codeTagEnd))) {
            return null;
        }
        final int codeEnd = html.indexOf(CODE_END, codeTagEnd);
        if (codeEnd < 0) {
            return null;
        }
        final String content = html.substring(codeTagEnd + 1, codeEnd);
        int preEnd = codeEnd + CODE_END.length();
        while (preEnd < html.length() && Character.isWhitespace(html.charAt(preEnd))) {
            preEnd++;
        }
        // Only text: a block with markup in it isn't one the converter made of a code block.
        if (!html.startsWith(PRE_END, preEnd) || content.indexOf('<') >= 0) {
            return null;
        }
        return new Block(unescape(content), preEnd + PRE_END.length());
    }

    // True if a tag's name ends here: at a space or the tag's end.
    private static boolean isTagName(final String html, final int index) {
        return index < html.length() && (html.charAt(index) == '>' || html.charAt(index) == ' ');
    }

    // True if a <code> tag's class attribute has the class of a Mermaid block.
    private static boolean hasMermaidClass(final String tag) {
        final int classAt = tag.indexOf("class=\"");
        if (classAt < 0) {
            return false;
        }
        final int valueStart = classAt + "class=\"".length();
        final int valueEnd = tag.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return false;
        }
        for (final String name : tag.substring(valueStart, valueEnd).split(" ")) {
            if (LANGUAGE_CLASS.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String reference(final String name) {
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            case "nbsp":
                return " ";
            default:
                break;
        }
        if (name.length() > 1 && name.charAt(0) == '#') {
            final boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
            try {
                final int code = Integer.parseInt(name.substring(hex
                        ? 2
                        : 1), hex
                        ? 16
                        : 10);
                return code > 0 && code < 0xFFFF
                        ? String.valueOf((char) code)
                        : null;
            } catch (final NumberFormatException e) {
                return null;
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    // A Mermaid code block's text, and where its </pre> ends.
    private record Block(String text, int end) {

    }
}
