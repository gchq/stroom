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

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/// A long Documentation doc (a vendor manual, say) read where it matters rather than all at once, as the Stroom
/// MCP server's `describe_document` reads one: its outline, or the passages that mention something, e.g. an event
/// id.
final class DocumentationPassages {

    /// The most characters of passages given in one result.
    static final int DOC_CHARS = 30_000;
    /// The most headings given in an outline.
    static final int MAX_OUTLINE = 300;
    /// The most lines kept around each match.
    static final int MAX_CONTEXT_LINES = 60;
    /// The lines kept around each match by default.
    static final int DEFAULT_CONTEXT_LINES = 8;

    private static final Pattern HEADING = Pattern.compile("^#{1,4} ");

    private DocumentationPassages() {
        // Static only.
    }

    /// @param doc          What is said of the doc, e.g. its type, UUID and name, which the result starts with.
    /// @param text         The doc's text.
    /// @param find         What to find (case-insensitive; several terms separated by `|`).
    /// @param contextLines The lines kept around each match, from 0 to [#MAX_CONTEXT_LINES].
    /// @return The doc's fields, then `characters`, `find`, `matches` (how many lines mention it) and `passages`
    /// (each `lines`, e.g. `12343-12349`, and `text`), at most [#DOC_CHARS] characters of them, and a `note` if
    /// there are more, or none.
    static Map<String, Object> passages(final Map<String, Object> doc,
                                        final String text,
                                        final String find,
                                        final int contextLines) {
        final List<String> lines = NullSafe.string(text).lines().toList();
        final Map<String, Object> out = new LinkedHashMap<>(doc);
        out.put("characters", NullSafe.string(text).length());
        final List<String> terms = Arrays.stream(NullSafe.string(find).split("\\|"))
                .map(term -> term.strip().toLowerCase(Locale.ROOT))
                .filter(term -> !term.isEmpty())
                .toList();
        final int context = Math.max(0, Math.min(contextLines, MAX_CONTEXT_LINES));
        final List<Integer> hits = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            final String line = lines.get(n).toLowerCase(Locale.ROOT);
            if (terms.stream().anyMatch(line::contains)) {
                hits.add(n);
            }
        }
        final List<int[]> spans = new ArrayList<>();
        for (final int n : hits) {
            final int lo = Math.max(0, n - context);
            final int hi = Math.min(lines.size(), n + context + 1);
            if (!spans.isEmpty() && lo <= spans.getLast()[1]) {
                spans.getLast()[1] = Math.max(spans.getLast()[1], hi);
            } else {
                spans.add(new int[]{lo, hi});
            }
        }
        final List<Map<String, Object>> shown = new ArrayList<>();
        int size = 0;
        for (final int[] span : spans) {
            final String passage = String.join("\n", lines.subList(span[0], span[1]));
            if (size + passage.length() > DOC_CHARS) {
                break;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("lines", (span[0] + 1) + "-" + span[1]);
            entry.put("text", passage);
            shown.add(entry);
            size += passage.length();
        }
        out.put("find", find);
        out.put("matches", hits.size());
        out.put("passages", shown);
        if (shown.size() < spans.size()) {
            out.put("note", (spans.size() - shown.size()) + " more passages: narrow find, or lower contextLines");
        }
        if (hits.isEmpty()) {
            out.put("note", "Nothing mentions it: try another term (the vendor's name for the event or field).");
        }
        return out;
    }

    /// @param text A doc's text.
    /// @return Its headings (levels 1 to 4), in order, at most [#MAX_OUTLINE].
    static List<String> outline(final String text) {
        return NullSafe.string(text).lines()
                .filter(line -> HEADING.matcher(line).find())
                .map(String::strip)
                .limit(MAX_OUTLINE)
                .toList();
    }
}
