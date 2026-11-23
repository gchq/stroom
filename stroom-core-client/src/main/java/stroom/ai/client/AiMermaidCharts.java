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
import stroom.svg.shared.SvgImage;

/// Draws the charts in an answer: after the shared Markdown converter has turned the answer to sanitised HTML,
/// each ```` ```mermaid ```` code block it rendered (`<pre><code class="... language-mermaid">`) whose text is a
/// gantt chart of the subset ([MermaidGanttParser]) is replaced by Stroom's own SVG of it ([AiGanttChart]). A
/// block that isn't (another kind of chart, or one outside the subset) stays as code: nothing in it is ever run.
/// Only the chat does this; the converter and every other use of it are unchanged.
///
/// A chart's element keeps the block's text (escaped, in [#SOURCE_ATTRIBUTE]) so it can be drawn again, zoomed or
/// at another width ([AiGanttInteraction]), and has buttons to reset the zoom (shown only when zoomed) and to show
/// it in a larger dialog.
final class AiMermaidCharts {

    /// The class of the element a chart is drawn in.
    static final String CHART_CLASS = "ai-gantt";
    /// The attribute of a chart's element holding the block's text.
    static final String SOURCE_ATTRIBUTE = "data-gantt";
    /// The attribute of a chart's element holding the span it is zoomed to ([AiGanttZoom#toAttribute]); absent when
    /// it shows the whole period.
    static final String VIEW_ATTRIBUTE = "data-gantt-view";
    /// The attribute of a chart's element holding the width it is drawn at, when not [AiGanttChart#WIDTH].
    static final String WIDTH_ATTRIBUTE = "data-gantt-width";
    /// The class of the element holding a chart's SVG, drawn again in place.
    static final String PLOT_CLASS = "ai-gantt-plot";
    /// The class of the button that shows the whole period again.
    static final String RESET_CLASS = "ai-gantt-reset";
    /// The class of the button that shows a chart in a larger dialog.
    static final String MAXIMISE_CLASS = "ai-gantt-maximise";

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
        final MermaidGantt gantt = parse(text);
        return gantt == null
                ? null
                : chart(text, gantt, null, AiGanttChart.WIDTH, true);
    }

    /// @param text A Mermaid code block's text.
    /// @return The chart it holds, or null if it isn't a gantt chart of the subset.
    static MermaidGantt parse(final String text) {
        if (text == null) {
            return null;
        }
        try {
            return MermaidGanttParser.parse(text);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /// @param text        The block's text.
    /// @param gantt       The chart it holds.
    /// @param view        The span to draw, or null for the whole period.
    /// @param width       The width to draw it at.
    /// @param maximisable True for a button to show it in a larger dialog (not in the dialog itself).
    /// @return The chart's element: its buttons, then its SVG in an element of [#PLOT_CLASS]; the text kept,
    /// escaped, so it can be drawn again.
    static String chart(final String text,
                        final MermaidGantt gantt,
                        final long[] view,
                        final int width,
                        final boolean maximisable) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"").append(CHART_CLASS).append("\" ").append(SOURCE_ATTRIBUTE).append("=\"")
                .append(attribute(text)).append('"');
        if (view != null) {
            sb.append(' ').append(VIEW_ATTRIBUTE).append("=\"").append(AiGanttZoom.toAttribute(view)).append('"');
        }
        if (width != AiGanttChart.WIDTH) {
            sb.append(' ').append(WIDTH_ATTRIBUTE).append("=\"").append(width).append('"');
        }
        sb.append("><div class=\"ai-gantt-tools\"><span class=\"ai-gantt-hint\">Drag across the times to zoom")
                .append("</span>");
        sb.append(button(RESET_CLASS, SvgImage.UNDO, "Reset zoom", "Show the whole period again", view == null));
        if (maximisable) {
            sb.append(button(MAXIMISE_CLASS, SvgImage.MAXIMISE, "Maximise", "Show the chart in a larger dialog",
                    false));
        }
        sb.append("</div><div class=\"").append(PLOT_CLASS).append("\">").append(AiGanttChart.svg(gantt, view, width))
                .append("</div></div>");
        return sb.toString();
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

    // A small button of the chart's, its label its text: of constants only.
    private static String button(final String className,
                                 final SvgImage icon,
                                 final String label,
                                 final String title,
                                 final boolean hidden) {
        return "<button type=\"button\" class=\"ai-gantt-button " + className + "\" title=\"" + title
               + "\" aria-label=\"" + title + "\"" + (hidden
                ? " hidden"
                : "") + "><div class=\"svgIcon " + icon.getClassName() + "\">" + icon.getSvg() + "</div><span>"
               + label + "</span></button>";
    }

    // Text escaped for an attribute's value, its line breaks kept as references.
    private static String attribute(final String text) {
        return AiChatOverview.escape(text).replace("\r", "").replace("\n", "&#10;");
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
