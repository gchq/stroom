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

/// What the user typed in the chat, as Markdown that shows it as typed: markup outside code (XML they paste, say)
/// is escaped, so it is shown rather than taken as HTML, which the sanitiser then removed with everything in it (seen:
/// `<Data Name="mentions"> ... </Data>` sent in a message showed as nothing). Code (fenced or indented blocks, and
/// backtick spans) and autolinks (`<https://...>`) are left as Markdown reads them.
final class AiUserMarkdown {

    private AiUserMarkdown() {
        // Static only.
    }

    /// @param markdown What the user typed.
    /// @return It with `<` and `&` outside code escaped, so the Markdown shows them as typed.
    static String escapeHtml(final String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        final StringBuilder out = new StringBuilder(markdown.length());
        String fence = null;
        final String[] lines = markdown.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String trimmed = line.stripLeading();
            if (fence != null) {
                if (trimmed.startsWith(fence)) {
                    fence = null;
                }
                out.append(line);
            } else if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fence = trimmed.substring(0, 3);
                out.append(line);
            } else if (line.startsWith("    ") || line.startsWith("\t")) {
                // An indented code block, or a list item's continuation, shown as it is.
                out.append(line);
            } else {
                escapeLine(line, out);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    // A line outside any code block: backtick spans and autolinks kept, the rest escaped.
    private static void escapeLine(final String line, final StringBuilder out) {
        int i = 0;
        while (i < line.length()) {
            final char c = line.charAt(i);
            if (c == '`') {
                final int run = runOf(line, i, '`');
                final int close = closingRun(line, i + run, run);
                if (close >= 0) {
                    out.append(line, i, close + run);
                    i = close + run;
                    continue;
                }
                out.append(line, i, i + run);
                i += run;
            } else if (c == '<' && isAutolink(line, i)) {
                final int end = line.indexOf('>', i);
                out.append(line, i, end + 1);
                i = end + 1;
            } else if (c == '<') {
                out.append("&lt;");
                i++;
            } else if (c == '&') {
                out.append("&amp;");
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
    }

    private static int runOf(final String line, final int from, final char c) {
        int end = from;
        while (end < line.length() && line.charAt(end) == c) {
            end++;
        }
        return end - from;
    }

    // Where a run of exactly `run` backticks starts at or after `from`, or -1.
    private static int closingRun(final String line, final int from, final int run) {
        int i = line.indexOf('`', from);
        while (i >= 0) {
            final int length = runOf(line, i, '`');
            if (length == run) {
                return i;
            }
            i = line.indexOf('`', i + length);
        }
        return -1;
    }

    // `<scheme:...>` with no space or `<` before its `>`, as Markdown reads an autolink.
    private static boolean isAutolink(final String line, final int at) {
        final int end = line.indexOf('>', at);
        if (end < 0) {
            return false;
        }
        // Lower-cased rather than matched with (?i), which JavaScript's regular expressions (GWT's) don't take.
        final String inner = line.substring(at + 1, end).toLowerCase();
        return inner.matches("(https?|ftp|mailto):[^\\s<]+");
    }
}
