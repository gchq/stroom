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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// A survey_feed result too large for a client outside Stroom ([LargeResults]): seen in eval cases 36, 56, 61, 65
/// and 66 on Haiku, results of 52,000 to 109,000 characters, nearly all the shapes (each with its examples and
/// locations) and the locations to step. Outlined: the locations counted, stepped all at once from the saved file
/// (step_records locations_from=); then each shape without its locations and further examples, its example cut
/// short; then without its example. A shape's signature, which the agent names a shape by, is kept whole.
final class SurveyOutline {

    private static final String SHAPES = "shapes";
    private static final String LOCATIONS = "locations";
    private static final String EXAMPLE = "example";
    // The most characters of a shape's example kept in its outline.
    private static final int EXAMPLE_CHARS = 200;

    private SurveyOutline() {
        // Static only.
    }

    /// Gives a survey_feed result in outline when it is too large for the caller ([LargeResults#apply]).
    ///
    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param key       The survey's build, else its feed.
    /// @param result    The result, changed in place.
    /// @param limit     The most characters it is given whole in.
    /// @return True if it was given in outline.
    static boolean outlineIfLarge(final ToolCaller caller,
                                  final Optional<SessionWorkspace> workspace,
                                  final String key,
                                  final Map<String, Object> result,
                                  final int limit) {
        return LargeResults.of(SurveyFeedTool.NAME, key)
                .keep("feed", "format", "coverage", "source_types", "kind_fields", "survey_doc", "scope",
                        "scope_read", "read_again", "saturated", "streams_read", "records_read", "new_shapes",
                        "time_range", "quoted_newlines", "survey", "carried")
                .outline(LOCATIONS, SurveyOutline::locations)
                .outline(SHAPES, SurveyOutline::shapes)
                .apply(caller, workspace, result, limit);
    }

    // The records to step, counted by stream, and stepped all at once from the saved file.
    private static Object locations(final Object value, final LargeResults.Room room) {
        final List<Map<String, Object>> locations = JsonValues.maps(value);
        if (locations.isEmpty()) {
            return value;
        }
        final Set<Object> streams = new LinkedHashSet<>();
        int untranslated = 0;
        for (final Map<String, Object> location : locations) {
            streams.add(location.get("stream"));
            if (StepRecordsTool.EXPECT_NONE.equals(location.get("expect"))) {
                untranslated++;
            }
        }
        final Map<String, Object> outlined = new LinkedHashMap<>();
        outlined.put("records", locations.size());
        outlined.put("streams", List.copyOf(streams));
        if (untranslated > 0) {
            outlined.put("left_untranslated", untranslated);
        }
        outlined.put("step_them", StepRecordsTool.NAME + " " + StepRecordsTool.LOCATIONS_FROM_ARG + "='"
                                  + room.saved() + "' steps every one of them (a record of each shape), "
                                  + "read from the saved survey: no need to list them.");
        return outlined;
    }

    // Each shape without its locations and further examples, its example cut short; left out too when the shapes
    // still don't fit their room.
    private static Object shapes(final Object value, final LargeResults.Room room) {
        final List<Object> withExamples = shapes(value, true);
        return LargeResults.length(withExamples) <= room.target()
                ? withExamples
                : shapes(value, false);
    }

    private static List<Object> shapes(final Object value, final boolean withExample) {
        final List<Object> outlined = new ArrayList<>();
        for (final Object entry : JsonValues.listOrEmpty(value)) {
            if (!(entry instanceof final Map<?, ?> shape)) {
                outlined.add(entry);
                continue;
            }
            final Map<String, Object> said = new LinkedHashMap<>();
            shape.forEach((k, v) -> {
                final String key = String.valueOf(k);
                if (EXAMPLE.equals(key) && withExample && v instanceof final String text) {
                    said.put(key, LargeResults.head(text, EXAMPLE_CHARS));
                } else if (!EXAMPLE.equals(key) && !LOCATIONS.equals(key) && !"more_examples".equals(key)) {
                    said.put(key, v);
                }
            });
            outlined.add(said);
        }
        return outlined;
    }
}
