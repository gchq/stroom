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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.LargeResults;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.workspace.SessionWorkspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A write_documentation result too large for a client outside Stroom ([LargeResults]): seen in eval cases 35, 36
/// and 56 on Haiku, results of 73,000 to 88,000 characters, almost all the generated Field mapping section given
/// back for the agent to check. Outlined: the section (or Reference maps) by its headings and size, written to the
/// doc as generated and saved whole; the fields to describe (needs_descriptions) each with where its value comes from
/// cut short; the doc, its link and what comes next kept whole.
final class DocumentationOutline {

    private static final String FROM = "from";
    // The most characters of where a field to describe comes from said in an outline.
    private static final int FROM_CHARS = 300;

    private DocumentationOutline() {
        // Static only.
    }

    /// Gives a write_documentation result in outline when it is too large for the caller ([LargeResults#apply]).
    ///
    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param key       The build, else the documented pipeline or index.
    /// @param result    The result, changed in place.
    /// @param limit     The most characters it is given whole in.
    /// @return True if it was given in outline.
    static boolean outlineIfLarge(final ToolCaller caller,
                                  final Optional<SessionWorkspace> workspace,
                                  final String key,
                                  final Map<String, Object> result,
                                  final int limit) {
        return LargeResults.of(WriteDocumentationTool.NAME, key)
                .keep("type", "uuid", "name", "written", "link", "note", "working_copy_of",
                        "accept_errors_already_recorded", "fields", "confirmation_id", "field_mapping_needs")
                .outline("fields", (value, room) -> fieldsToDescribe(value))
                .outline("field_mapping", DocumentationOutline::section)
                .outline("reference_maps", DocumentationOutline::section)
                .apply(caller, workspace, result, limit);
    }

    // The fields to describe (needs_descriptions), each whole but where its value comes from, cut short: seen in
    // eval case 68 (300 event types), 46,000 characters, each TypeId and Description listed in `from`.
    private static Object fieldsToDescribe(final Object value) {
        final List<Object> fields = new ArrayList<>();
        for (final Object entry : JsonValues.listOrEmpty(value)) {
            if (entry instanceof final Map<?, ?> field && field.get(FROM) instanceof final String from
                && from.length() > FROM_CHARS) {
                final Map<String, Object> said = new LinkedHashMap<>();
                field.forEach((k, v) -> said.put(String.valueOf(k), v));
                said.put(FROM, from.substring(0, FROM_CHARS) + "... (" + from.lines().count() + " lines in all: the "
                               + "rest in the saved result)");
                fields.add(said);
            } else {
                fields.add(entry);
            }
        }
        return fields;
    }

    // A generated section in brief: its size and headings, as written to the doc.
    private static Object section(final Object value, final LargeResults.Room room) {
        if (!(value instanceof final String markdown)) {
            return value;
        }
        final List<String> lines = markdown.lines().toList();
        final Map<String, Object> outlined = new LinkedHashMap<>();
        outlined.put("written", "As generated, in the doc: this is its outline. Check it there (the link), or read "
                                + "a part of it from the saved result.");
        outlined.put("characters", markdown.length());
        outlined.put("lines", lines.size());
        outlined.put("headings", lines.stream()
                .filter(line -> line.startsWith("#"))
                .toList());
        return outlined;
    }
}
