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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.validation.XsltVersionHistory;

import java.util.ArrayList;
import java.util.List;

/// What an XSLT has that the expected code hasn't (`+`) and the reverse (`-`), element by element: both
/// pretty-printed alike, so only real differences show. For a hand edit: what to carry into the mapping.
public final class CodeDiff {

    /// How many changed lines are shown by default.
    public static final int LIMIT = 12;

    private static final int LONGEST_LINE = 160;

    private CodeDiff() {
        // Static only.
    }

    /// @param expected The code expected (e.g. regenerated from the mapping).
    /// @param actual   The code as it is.
    /// @return The changed lines, at most [#LIMIT], then how many more there are.
    public static List<String> diff(final String expected, final String actual) {
        return diff(expected, actual, LIMIT);
    }

    /// @param expected The code expected (e.g. regenerated from the mapping).
    /// @param actual   The code as it is.
    /// @param limit    How many changed lines to show.
    /// @return The changed lines (`+ <number key="bytes">`), each at most 160 characters (then `…`), then
    /// `... and N more lines` if there are more than limit.
    public static List<String> diff(final String expected, final String actual, final int limit) {
        final List<String> changed = new ArrayList<>();
        for (final String line : LineDiff.changes(lines(expected), lines(actual))) {
            if (!line.startsWith("+++") && !line.startsWith("---")) {
                changed.add(line.charAt(0) + " " + line.substring(1));
            }
        }
        final List<String> shown = new ArrayList<>();
        for (final String line : changed.subList(0, Math.min(limit, changed.size()))) {
            shown.add(line.codePointCount(0, line.length()) <= LONGEST_LINE
                    ? line
                    : line.substring(0, line.offsetByCodePoints(0, LONGEST_LINE)) + "…");
        }
        if (changed.size() > limit) {
            shown.add("... and " + (changed.size() - limit) + " more lines");
        }
        return shown;
    }

    // The code's lines, pretty-printed without comments or whitespace between its elements (or as it is, if it
    // isn't well-formed), each stripped, blank ones left out.
    private static List<String> lines(final String code) {
        final String stripped = XsltVersionHistory.strip(code);
        String text;
        try {
            text = XmlSerialiser.toPrettyXml(XmlParser.parse(stripped, true, true));
        } catch (final XmlParser.XmlParseException e) {
            text = stripped;
        }
        final List<String> out = new ArrayList<>();
        for (final String line : ToolText.splitlines(text)) {
            final String trimmed = ToolText.strip(line);
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
