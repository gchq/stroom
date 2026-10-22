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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.PyText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The text of the comments the generated XSLTs carry (the Stroom MCP server's `_comment` in `utils/xsltgen.py`):
/// no `--` (a comment can't hold it), and wrapped as Python's `textwrap.wrap` wraps it, its later lines indented
/// under the first.
public final class XsltComments {

    /// The width comments are wrapped to.
    public static final int WIDTH = 112;

    private static final Pattern DASHES = Pattern.compile("-{2,}");
    private static final Pattern SPACES = Pattern.compile("( +)");
    private static final String FIRST_INDENT = " ";
    private static final String LATER_INDENT = "       ";
    private static final int TAB_SIZE = 8;

    private XsltComments() {
        // Static only.
    }

    /// @param text What the comment says.
    /// @return The comment's text, wrapped at [#WIDTH].
    public static String comment(final String text) {
        return comment(text, WIDTH);
    }

    /// @param text  What the comment says.
    /// @param width The width to wrap it to.
    /// @return The comment's text: dashes runs made one, trailing dashes dropped, wrapped, with a space either side.
    public static String comment(final String text, final int width) {
        final String single = PyText.rstrip(DASHES.matcher(text).replaceAll("-"), "-");
        return String.join("\n", wrap(single, width, FIRST_INDENT, LATER_INDENT)) + " ";
    }

    /// Python's `textwrap.wrap(text, width, initial_indent=..., subsequent_indent=..., break_long_words=False,
    /// break_on_hyphens=False)`.
    ///
    /// @param text        The text.
    /// @param width       The width of each line.
    /// @param firstIndent What the first line starts with.
    /// @param laterIndent What the others start with.
    /// @return The lines.
    public static List<String> wrap(final String text,
                                    final int width,
                                    final String firstIndent,
                                    final String laterIndent) {
        final List<String> chunks = new ArrayList<>(chunks(munged(text)));
        final List<String> lines = new ArrayList<>();
        // Taken from the end, as textwrap reverses its chunks and pops them.
        int next = 0;
        while (next < chunks.size()) {
            final String indent = lines.isEmpty()
                    ? firstIndent
                    : laterIndent;
            final int room = width - length(indent);
            if (!lines.isEmpty() && PyText.isBlank(chunks.get(next))) {
                next++;
            }
            final List<String> line = new ArrayList<>();
            int used = 0;
            while (next < chunks.size()) {
                final int size = length(chunks.get(next));
                if (used + size <= room) {
                    line.add(chunks.get(next++));
                    used += size;
                } else {
                    break;
                }
            }
            // A word longer than a line, never broken: on a line of its own.
            if (next < chunks.size() && length(chunks.get(next)) > room && line.isEmpty()) {
                line.add(chunks.get(next++));
            }
            if (!line.isEmpty() && PyText.isBlank(line.getLast())) {
                line.removeLast();
            }
            if (!line.isEmpty()) {
                lines.add(indent + String.join("", line));
            }
        }
        return lines;
    }

    // Tabs expanded, and every white space character textwrap knows made a space.
    private static String munged(final String text) {
        final StringBuilder out = new StringBuilder();
        int column = 0;
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (ch == '\t') {
                final int spaces = TAB_SIZE - (column % TAB_SIZE);
                out.append(" ".repeat(spaces));
                column += spaces;
            } else if (ch == '\n' || ch == '\r') {
                out.append(' ');
                column = 0;
            } else if (ch == 0x0b || ch == 0x0c) {
                out.append(' ');
                column++;
            } else {
                out.append(ch);
                column++;
            }
        }
        return out.toString();
    }

    // The words and the runs of spaces between them, in order.
    private static List<String> chunks(final String text) {
        final List<String> out = new ArrayList<>();
        final Matcher matcher = SPACES.matcher(text);
        int at = 0;
        while (matcher.find()) {
            if (matcher.start() > at) {
                out.add(text.substring(at, matcher.start()));
            }
            out.add(matcher.group());
            at = matcher.end();
        }
        if (at < text.length()) {
            out.add(text.substring(at));
        }
        return out;
    }

    private static int length(final String text) {
        return text.codePointCount(0, text.length());
    }
}
