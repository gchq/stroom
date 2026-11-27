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

package stroom.ai.impl.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The text a form shows the user, in sentence case: each bullet or numbered line starts with a capital, as the user
/// asked (2026-11-24: forms' bullets, e.g. "- build: …", "- pipeline: …", were lower case). Only a plain word is
/// capitalised (letters, then a space, a colon, a quote or the line's end), so names and code (`event.action`,
/// `stream_ids`, "e.g.", a backticked name, a link) are left as written, as is anything in a fenced code block.
/// Applied where every form is sent ([ChatUserQuestions], and the MCP server's own), so each tool's form, and the
/// agent's own questions, read alike.
public final class FormText {

    // A bullet or numbered line's start, then its first word: letters only, followed by what ends a plain word.
    private static final Pattern LIST_LINE = Pattern.compile(
            "^(\\s*(?:[-*+]|\\d+[.)])\\s+)([a-z][a-z]*)(?=[ :'\"’]|$)");
    // A list line that is a label and its value: a short label of words (no code, link or emphasis), a colon, then
    // the value.
    private static final Pattern LABELLED_LINE = Pattern.compile(
            "^(\\s*(?:[-*+]|\\d+[.)])\\s+)([A-Za-z][A-Za-z0-9 '’()/_.-]{0,48}?):(\\s+\\S.*)$");
    private static final String FENCE = "```";

    private FormText() {
        // Static only.
    }

    /// A form's text with each bullet or numbered line starting with a capital.
    ///
    /// @param text The text, or null.
    /// @return The text, its list lines in sentence case; null for null.
    public static String sentenceCase(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final String[] lines = text.split("\n", -1);
        final StringBuilder out = new StringBuilder(text.length());
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.strip().startsWith(FENCE)) {
                inFence = !inFence;
            }
            if (i > 0) {
                out.append('\n');
            }
            out.append(inFence
                    ? line
                    : capitalised(line));
        }
        return out.toString();
    }

    /// A form's text as the chat shows it (Markdown): its list lines in sentence case ([#sentenceCase]), and the label
    /// of each that is a label and its value in bold (the user, 2026-11-24: "- **Build:** azure-audit-v1.0", so the
    /// labels stand out). Not for an MCP client, which may show a form's text as it is, asterisks and all.
    ///
    /// @param text The text, or null.
    /// @return The text, its list lines in sentence case and their labels bold; null for null.
    public static String forChat(final String text) {
        final String cased = sentenceCase(text);
        if (cased == null || cased.isEmpty()) {
            return cased;
        }
        final String[] lines = cased.split("\n", -1);
        final StringBuilder out = new StringBuilder(cased.length() + 16);
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.strip().startsWith(FENCE)) {
                inFence = !inFence;
            }
            if (i > 0) {
                out.append('\n');
            }
            final Matcher matcher = inFence
                    ? null
                    : LABELLED_LINE.matcher(line);
            out.append(matcher != null && matcher.matches()
                    ? matcher.group(1) + "**" + matcher.group(2).strip() + ":**" + matcher.group(3)
                    : line);
        }
        return out.toString();
    }

    // A list line with its first plain word capitalised; any other line as it is.
    private static String capitalised(final String line) {
        final Matcher matcher = LIST_LINE.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        final String word = matcher.group(2);
        return matcher.group(1) + Character.toUpperCase(word.charAt(0)) + word.substring(1)
               + line.substring(matcher.end());
    }
}
