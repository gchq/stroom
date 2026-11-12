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
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The records the build's survey found further into a sample's streams than the sample is read to, added to the
/// records a mapping is checked against. Seen in testing: a 25,000-record stream's sample was read to its 4,004th
/// record, so the fields of the rare kinds near its end (a file upload's name and size) were refused as in none of
/// the sample's records, and the agent read them through raw XPath to get past the check, which then checked none
/// of them; processing then warned that no rule read them.
///
/// Only for records a line each: a record of several lines, an XML element or a JSON array's item, can't be told
/// apart from the text around it, so the survey's examples of those are left out.
final class SurveyExamples {

    private SurveyExamples() {
        // Static methods only.
    }

    /// @param texts  The sample's texts, by name.
    /// @param cut    The parts the sample read only in part.
    /// @param states The build's survey states.
    /// @return A text for each cut part the survey found records in that its text doesn't hold: those records, after
    /// the part's header line where its records follow one.
    static Added added(final Map<String, String> texts,
                       final List<SampleStreamTexts.Cut> cut,
                       final List<Map<String, Object>> states) {
        return added(texts, cut, states, null);
    }

    /// As [#added(Map, List, List)], through a build's scope over an aggregated feed's source types: only the
    /// scope's records are added.
    ///
    /// @param texts  The sample's texts as read (not through the scope), by name.
    /// @param cut    The parts the sample read only in part.
    /// @param states The build's survey states.
    /// @param scope  The build's scope, or null for none.
    /// @return A text for each cut part the survey found records of the scope in that its text doesn't hold.
    static Added added(final Map<String, String> texts,
                       final List<SampleStreamTexts.Cut> cut,
                       final List<Map<String, Object>> states,
                       final SourceTypeScope scope) {
        final List<String> added = new ArrayList<>();
        final Set<Long> streams = new LinkedHashSet<>();
        int records = 0;
        for (final SampleStreamTexts.Cut part : cut) {
            final String text = texts.get(part.name());
            if (text == null) {
                continue;
            }
            final RecordSplitter.Chunk head;
            try {
                // Cut short: its last record may be cut off.
                head = RecordSplitter.split(text, RecordSplitter.MAX_RECORDS, true);
            } catch (final RuntimeException e) {
                continue;
            }
            if (!aLineEach(head)) {
                continue;
            }
            final Set<String> further = new LinkedHashSet<>();
            for (final Map<String, Object> state : states) {
                further.addAll(examplesIn(state, part, text));
            }
            if (further.isEmpty()) {
                continue;
            }
            String scoped = (head.header() == null
                    ? ""
                    : head.header() + "\n") + String.join("\n", further) + "\n";
            int count = further.size();
            if (scope != null) {
                final SourceTypeScope.Filtered filtered = scope.filter(scoped);
                if (filtered.filtered()) {
                    scoped = filtered.text();
                    count = filtered.kept();
                }
            }
            if (count > 0) {
                added.add(scoped);
                streams.add(part.stream());
                records += count;
            }
        }
        return new Added(added, records, List.copyOf(streams));
    }

    // Whether the part's records are a line each, so one can stand on its own (after the header line).
    private static boolean aLineEach(final RecordSplitter.Chunk head) {
        return !List.of(RecordSplitter.XML, RecordSplitter.XML_FRAGMENTS, RecordSplitter.JSON_ARRAY,
                        RecordSplitter.JSON_OBJECT).contains(head.format())
               && head.records().stream().noneMatch(record -> record.indexOf('\n') >= 0);
    }

    // The examples the survey kept from the part that its text doesn't hold, whole (not cut to the most kept).
    private static List<String> examplesIn(final Map<String, Object> state,
                                           final SampleStreamTexts.Cut part,
                                           final String text) {
        final List<String> out = new ArrayList<>();
        JsonValues.mapOrEmpty(state.get("shapes")).values().forEach(shape -> {
            if (!(shape instanceof final Map<?, ?> map)) {
                return;
            }
            for (final Object item : JsonValues.listOrEmpty(map.get("examples"))) {
                if (item instanceof final Map<?, ?> example
                    && example.get("text") instanceof final String record
                    && example.get("location") instanceof final Map<?, ?> location
                    && location.get("stream") instanceof final Number stream
                    && stream.longValue() == part.stream()
                    && partOf(location) == part.part()
                    && record.length() < SurveyDoc.MAX_EXAMPLE_CHARS
                    && record.indexOf('\n') < 0
                    && !text.contains(record)) {
                    out.add(record);
                }
            }
        });
        return out;
    }

    private static int partOf(final Map<?, ?> location) {
        return location.get("part") instanceof final Number part
                ? part.intValue()
                : 0;
    }


    // --------------------------------------------------------------------------------


    /// The texts added.
    ///
    /// @param texts   A text for each part with records added.
    /// @param records How many records they hold.
    /// @param streams The streams they are from.
    record Added(List<String> texts, int records, List<Long> streams) {

    }
}
