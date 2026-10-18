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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/// An overview of a chat, to find a part of it: a vertical flowchart of what the user did (their questions, what
/// they sent while the agent worked, their answers to its questions and their approvals), joined by lines saying
/// which tools the agent called in between and how long it spent. It is drawn as SVG, so it stays sharp however
/// far it is zoomed.
final class AiChatOverview {

    /// The attribute marking a step's shape with the id of its message, to go to it when clicked.
    static final String MESSAGE_ID_ATTRIBUTE = "data-overview-message-id";
    /// The width the overview is drawn at, before it is zoomed.
    static final int WIDTH = 360;

    private static final int CENTRE = WIDTH / 2;
    private static final int NODE_X = 36;
    private static final int NODE_WIDTH = WIDTH - 2 * NODE_X;
    private static final int TOP = 16;
    private static final int BOTTOM = 16;
    private static final int NODE_PADDING = 10;
    private static final int CAPTION_HEIGHT = 14;
    private static final int LINE_HEIGHT = 17;
    // How much of a step's text fits on a line, and how many lines it may have.
    private static final int LINE_CHARS = 38;
    private static final int MAX_LINES = 3;
    private static final int LINK_HEIGHT = 56;
    private static final int SHORT_LINK_HEIGHT = 24;
    private static final int LABEL_X = CENTRE + 10;
    private static final int LABEL_CHARS = 24;
    private static final int END_RADIUS = 4;
    // Tools that only turn groups of tools on, which say nothing of what was done.
    private static final String ACTIVATE_PREFIX = "activate_";

    private AiChatOverview() {
        // Static only.
    }

    /// @param entries The chat's messages, in order, as what the user did, the tools the agent called and
    ///                anything else the agent did.
    /// @return The steps the user took and what followed each.
    static Overview of(final List<Item> entries) {
        final List<Step> steps = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        List<String> tools = null;
        long stepTimeMs = 0;
        long lastTimeMs = 0;
        for (final Item entry : entries) {
            if (entry.kind() != null) {
                if (tools != null) {
                    links.add(new Link(tools, lastTimeMs - stepTimeMs));
                }
                steps.add(new Step(entry.messageId(), entry.kind(), entry.text()));
                tools = new ArrayList<>();
                stepTimeMs = entry.timeMs();
                lastTimeMs = entry.timeMs();
            } else if (tools != null) {
                // What the agent did before the first step, if anything, belongs to no step.
                if (entry.tool() != null && !entry.tool().startsWith(ACTIVATE_PREFIX)) {
                    tools.add(entry.tool());
                }
                lastTimeMs = Math.max(lastTimeMs, entry.timeMs());
            }
        }
        if (tools != null) {
            links.add(new Link(tools, lastTimeMs - stepTimeMs));
        }
        return new Overview(steps, links);
    }

    /// @param overview The chat's overview.
    /// @param working  True if the agent is working on the last step, to show it still going.
    /// @return The overview drawn as SVG, each step marked with [#MESSAGE_ID_ATTRIBUTE].
    static String svg(final Overview overview, final boolean working) {
        final StringBuilder links = new StringBuilder();
        final StringBuilder nodes = new StringBuilder();
        int y = TOP;
        final List<Step> steps = overview.steps();
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            final List<String> lines = wrap(step.text(), LINE_CHARS, MAX_LINES);
            final int height = 2 * NODE_PADDING + CAPTION_HEIGHT + lines.size() * LINE_HEIGHT;
            appendStep(nodes, step, lines, y, height);
            y += height;

            final Link link = overview.links().get(i);
            final boolean last = i == steps.size() - 1;
            final boolean live = last && working;
            if (!last || !link.isEmpty() || live) {
                final int length = link.isEmpty() && !live
                        ? SHORT_LINK_HEIGHT
                        : LINK_HEIGHT;
                appendLink(links, link, y, length, live, last);
                y += length + (last
                        ? END_RADIUS
                        : 0);
            }
        }
        if (steps.isEmpty()) {
            nodes.append("<text class=\"ai-overview-empty\" x=\"").append(CENTRE).append("\" y=\"").append(TOP + 16)
                    .append("\" text-anchor=\"middle\">Nothing asked yet</text>");
            y += 32;
        }
        final int height = y + BOTTOM;
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"ai-overview-svg\" viewBox=\"0 0 " + WIDTH + " "
               + height + "\" data-height=\"" + height + "\" role=\"img\" aria-label=\"Chat overview\">"
               + links + nodes + "</svg>";
    }

    private static void appendStep(final StringBuilder sb,
                                   final Step step,
                                   final List<String> lines,
                                   final int y,
                                   final int height) {
        sb.append("<g class=\"ai-overview-node ai-overview-node--").append(step.kind().cssName())
                .append("\" ").append(MESSAGE_ID_ATTRIBUTE).append("=\"").append(step.messageId())
                .append("\" tabindex=\"0\" role=\"button\">")
                .append("<title>").append(escape(step.kind().caption() + ": " + step.text()
                                                 + "\nClick to go to it in the chat.")).append("</title>")
                .append("<rect class=\"ai-overview-node-box\" x=\"").append(NODE_X).append("\" y=\"").append(y)
                .append("\" width=\"").append(NODE_WIDTH).append("\" height=\"").append(height)
                .append("\" rx=\"10\"/>")
                .append("<rect class=\"ai-overview-node-accent\" x=\"").append(NODE_X).append("\" y=\"").append(y)
                .append("\" width=\"4\" height=\"").append(height).append("\" rx=\"2\"/>")
                .append("<text class=\"ai-overview-node-caption\" x=\"").append(NODE_X + 14).append("\" y=\"")
                .append(y + NODE_PADDING + 10).append("\">").append(escape(step.kind().caption())).append("</text>")
                .append("<text class=\"ai-overview-node-text\">");
        int lineY = y + NODE_PADDING + CAPTION_HEIGHT + 13;
        for (final String line : lines) {
            sb.append("<tspan x=\"").append(NODE_X + 14).append("\" y=\"").append(lineY).append("\">")
                    .append(escape(line)).append("</tspan>");
            lineY += LINE_HEIGHT;
        }
        sb.append("</text></g>");
    }

    private static void appendLink(final StringBuilder sb,
                                   final Link link,
                                   final int y,
                                   final int length,
                                   final boolean live,
                                   final boolean last) {
        final String tools = toolSummary(link.tools(), Integer.MAX_VALUE);
        final String time = link.durationMs() >= 1000
                ? AiToolMessages.duration(link.durationMs())
                : null;
        final String described;
        if (tools.isEmpty()) {
            described = time != null
                    ? time
                    : "";
        } else {
            described = tools + (time != null
                    ? "\n" + time
                    : "");
        }
        sb.append("<g class=\"ai-overview-link").append(live
                        ? " ai-overview-link--live"
                        : "").append("\">");
        if (!described.isEmpty() || live) {
            sb.append("<title>").append(escape(live
                    ? "Working" + (described.isEmpty()
                            ? ""
                            : ":\n" + described)
                    : described)).append("</title>");
        }
        sb.append("<line x1=\"").append(CENTRE).append("\" y1=\"").append(y).append("\" x2=\"").append(CENTRE)
                .append("\" y2=\"").append(y + length).append("\"/>");
        if (last) {
            sb.append("<circle class=\"ai-overview-link-end\" cx=\"").append(CENTRE).append("\" cy=\"")
                    .append(y + length).append("\" r=\"").append(END_RADIUS).append("\"/>");
        }
        final List<String> label = new ArrayList<>();
        if (!tools.isEmpty()) {
            label.add(toolSummary(link.tools(), LABEL_CHARS));
        }
        if (time != null) {
            label.add(time);
        } else if (live && tools.isEmpty()) {
            label.add("Working…");
        }
        int labelY = y + length / 2 - (label.size() - 1) * 7 + 4;
        for (int i = 0; i < label.size(); i++) {
            sb.append("<text class=\"ai-overview-link-").append(i == 0 && !tools.isEmpty()
                            ? "tools"
                            : "time").append("\" x=\"").append(LABEL_X).append("\" y=\"").append(labelY)
                    .append("\">").append(escape(label.get(i))).append("</text>");
            labelY += 14;
        }
        sb.append("</g>");
    }

    /// @param tools    The tools called, in order, with repeats.
    /// @param maxChars The most characters the summary may have, beyond the count of those left out.
    /// @return The tools, each once with how many times it was called, in the order first called, e.g.
    /// "find_documents ×3, build_status", or as many as fit with how many more, e.g. "find_documents ×3, +2 more".
    static String toolSummary(final List<String> tools, final int maxChars) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String tool : tools) {
            counts.merge(tool, 1, Integer::sum);
        }
        final StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (final Entry<String, Integer> entry : counts.entrySet()) {
            final String part = entry.getKey() + (entry.getValue() > 1
                    ? " ×" + entry.getValue()
                    : "");
            final int length = sb.length() + (shown > 0
                    ? 2
                    : 0) + part.length();
            if (shown > 0 && length > maxChars) {
                break;
            }
            if (shown > 0) {
                sb.append(", ");
            }
            sb.append(part);
            shown++;
        }
        if (shown < counts.size()) {
            sb.append(", +").append(counts.size() - shown).append(" more");
        }
        return sb.toString();
    }

    /// @param text     Text, on any number of lines.
    /// @param maxChars The most characters a line may have.
    /// @param maxLines The most lines.
    /// @return The text as lines of whole words, the last ending "…" if not all of it fits.
    static List<String> wrap(final String text, final int maxChars, final int maxLines) {
        final List<String> lines = new ArrayList<>();
        final String[] words = (text == null
                ? ""
                : text.trim()).split("\\s+");
        StringBuilder line = new StringBuilder();
        boolean cut = false;
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            if (word.length() > maxChars) {
                word = word.substring(0, maxChars - 1) + "…";
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                lines.add(line.toString());
                line = new StringBuilder();
                if (lines.size() == maxLines) {
                    cut = true;
                    break;
                }
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!cut && line.length() > 0) {
            lines.add(line.toString());
        }
        if (cut) {
            final String lastLine = lines.get(lines.size() - 1);
            lines.set(lines.size() - 1, (lastLine.length() >= maxChars
                    ? lastLine.substring(0, maxChars - 1)
                    : lastLine) + "…");
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    // Text made safe to put in SVG.
    private static String escape(final String text) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /// What the user did, as a step in the overview.
    enum Kind {
        QUESTION("You asked", "question"),
        STEER("You added", "steer"),
        ANSWER("You answered", "answer"),
        APPROVAL("You approved", "approval"),
        REJECTION("You rejected", "rejection");

        private final String caption;
        private final String cssName;

        Kind(final String caption, final String cssName) {
            this.caption = caption;
            this.cssName = cssName;
        }

        /// @return What the step is, above its text, e.g. "You asked".
        String caption() {
            return caption;
        }

        /// @return The end of the step's class name, which colours it.
        String cssName() {
            return cssName;
        }
    }

    /// A message of the chat, for the overview.
    ///
    /// @param messageId The message's id.
    /// @param timeMs    When it was made.
    /// @param kind      What the user did, or null if it is something the agent did.
    /// @param text      What the user did, said shortly, for a step.
    /// @param tool      The tool called, for a tool call by the agent.
    record Item(int messageId, long timeMs, Kind kind, String text, String tool) {

        /// @return Something the user did.
        static Item step(final int messageId, final long timeMs, final Kind kind, final String text) {
            return new Item(messageId, timeMs, kind, text, null);
        }

        /// @return A tool the agent called.
        static Item tool(final int messageId, final long timeMs, final String tool) {
            return new Item(messageId, timeMs, null, null, tool);
        }

        /// @return Something else the agent did, e.g. answering, which counts towards the time spent.
        static Item work(final int messageId, final long timeMs) {
            return new Item(messageId, timeMs, null, null, null);
        }
    }

    /// Something the user did.
    record Step(int messageId, Kind kind, String text) {

    }

    /// What the agent did after a step, until the next.
    ///
    /// @param tools      The tools it called, in order, with repeats.
    /// @param durationMs How long it spent, from the step to the last thing it did.
    record Link(List<String> tools, long durationMs) {

        /// @return True if there is nothing to say of it.
        boolean isEmpty() {
            return tools.isEmpty() && durationMs < 1000;
        }
    }

    /// The overview of a chat: its steps, and after each, what followed it.
    record Overview(List<Step> steps, List<Link> links) {

    }
}
