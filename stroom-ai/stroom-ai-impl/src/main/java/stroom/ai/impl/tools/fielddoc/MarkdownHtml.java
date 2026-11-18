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

import stroom.util.shared.NullSafe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Keeps HTML out of generated documentation. Stroom renders a Documentation doc's Markdown with showdown and does
/// not sanitise it (a sandboxed frame: no scripts, but HTML, images and links render), so a value taken from the
/// data (a mail body's HTML, an XSLT fragment, a CDATA section) written as it is would be rendered as markup, hiding
/// or restyling the documentation. Inside a code span or a fenced code block showdown shows `<` as it is, so values
/// go in code ([#code]) or are escaped ([#text]); and [#escapeTags] escapes any tag left in a whole document outside
/// code, keeping the tools' own `<br>` line breaks, their hidden `<!-- stroom-mcp … -->` blocks and autolinks.
public final class MarkdownHtml {

    // A tag's start, a comment's, a processing instruction's or a declaration's (CDATA): '<' and a letter, '/', '!'
    // or '?'. Not "a < b" or "<5".
    private static final Pattern TAG_START = Pattern.compile("<(?=[A-Za-z/!?])");
    // What the tools write themselves and keep: a line break in a table cell, and an autolink (<https://...>).
    private static final Pattern KEPT = Pattern.compile("\\A(?:<br\\s*/?>|<(?:https?|mailto):[^\\s<>]*>)",
            Pattern.CASE_INSENSITIVE);
    private static final String OWN_COMMENT = "<!-- stroom-mcp";
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
    private static final Pattern WHITE_SPACE = Pattern.compile("[\\s\\u2028\\u2029\\u0085]+");

    private MarkdownHtml() {
        // Static only.
    }

    /// @param value Text from the data, for a sentence or a table cell outside code.
    /// @return It with `&`, `<` and `>` as entities, so showdown shows them rather than reading markup.
    public static String text(final String value) {
        return NullSafe.string(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /// @param value A value from the data, e.g. a kind's name or a Data element's.
    /// @return It as inline code, on one line, whole: as [FieldDocs#sampleValue], with its backticks as quotes.
    public static String code(final String value) {
        return "`" + WHITE_SPACE.matcher(NullSafe.string(value)).replaceAll(" ").strip().replace('`', '\'') + "`";
    }

    /// Escapes the HTML tags in Markdown that are outside code: each `<` starting a tag, a comment, a processing
    /// instruction or a CDATA section becomes `&lt;`, so it shows as text. Code spans and fenced code blocks are
    /// left as they are (showdown shows their text as it is), as are the tools' own `<br>` line breaks, their hidden
    /// `<!-- stroom-mcp … -->` blocks (through the first `-->`, as a browser ends a comment) and autolinks. Applying
    /// it again changes nothing.
    ///
    /// @param markdown Markdown, e.g. the agent's documentation, or null.
    /// @return It with no tag outside code.
    public static String escapeTags(final String markdown) {
        final String text = NullSafe.string(markdown);
        final StringBuilder out = new StringBuilder(text.length());
        String fence = null;
        int at = 0;
        while (at < text.length()) {
            final int end = lineEnd(text, at);
            final String line = text.substring(at, end);
            final Matcher fenced = FENCE.matcher(line);
            if (fence != null) {
                out.append(line);
                if (fenced.find() && fenced.group(1).charAt(0) == fence.charAt(0)
                    && fenced.group(1).length() >= fence.length()) {
                    fence = null;
                }
                at = end;
                continue;
            }
            if (fenced.find()) {
                fence = fenced.group(1);
                out.append(line);
                at = end;
                continue;
            }
            at = inline(text, at, end, out);
        }
        return out.toString();
    }

    // The index just after the line starting at 'from' (its new line included).
    private static int lineEnd(final String text, final int from) {
        final int newLine = text.indexOf('\n', from);
        return newLine < 0
                ? text.length()
                : newLine + 1;
    }

    // The text of a line outside fenced code, from 'from' to 'end': code spans kept, an own comment kept through
    // its end (which may be lines on), every other tag escaped. Returns where it stopped: the line's end, or the
    // end of a comment ending on a later line.
    private static int inline(final String text, final int from, final int end, final StringBuilder out) {
        int i = from;
        while (i < end) {
            final char ch = text.charAt(i);
            if (ch == '`') {
                final int run = run(text, i, end);
                final int close = closing(text, i + run, end, run);
                if (close >= 0) {
                    out.append(text, i, close + run);
                    i = close + run;
                } else {
                    out.append(text, i, i + run);
                    i += run;
                }
                continue;
            }
            if (ch == '<') {
                if (text.startsWith(OWN_COMMENT, i)) {
                    final int closed = text.indexOf("-->", i + 4);
                    final int stop = closed < 0
                            ? text.length()
                            : closed + 3;
                    out.append(text, i, stop);
                    // One ending lines on: the rest of its last line is read from there.
                    if (stop > end) {
                        return stop;
                    }
                    i = stop;
                    continue;
                }
                final Matcher kept = KEPT.matcher(text.substring(i, end));
                if (kept.find()) {
                    out.append(kept.group());
                    i += kept.group().length();
                    continue;
                }
                if (TAG_START.matcher(text.substring(i, Math.min(end, i + 2))).lookingAt()) {
                    out.append("&lt;");
                    i++;
                    continue;
                }
            }
            out.append(ch);
            i++;
        }
        return end;
    }

    // The length of the run of backticks at 'from'.
    private static int run(final String text, final int from, final int end) {
        int i = from;
        while (i < end && text.charAt(i) == '`') {
            i++;
        }
        return i - from;
    }

    // Where a run of exactly 'length' backticks starts after 'from' on the line, or -1.
    private static int closing(final String text, final int from, final int end, final int length) {
        int i = from;
        while (i < end) {
            if (text.charAt(i) == '`') {
                final int run = run(text, i, end);
                if (run == length) {
                    return i;
                }
                i += run;
            } else {
                i++;
            }
        }
        return -1;
    }
}
