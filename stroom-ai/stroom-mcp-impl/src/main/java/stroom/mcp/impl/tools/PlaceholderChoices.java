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

import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.sample.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Values a source writes for none (`-`, `N/A`, ...; [Placeholders]), put to the user, a question for each value
/// whatever fields hold it:
///
/// - build_translation_xslt, before the XSLT is saved: leave them out of the events (the mapping's nil_values), or
///   keep them as written ([#OPTIONS]);
/// - draft_index_mapping for a discovery index: index them as missing, null, an empty string, or as written
///   ([#JSON_OPTIONS]);
/// - draft_index_mapping for an index of Events: as missing, null, or as written ([#EVENTS_OPTIONS]).
///
/// Not in the Stroom MCP server; asked for by the user (seen: W3C logs' `-` for a missing address went on into an
/// Elasticsearch ip field, which refused the documents).
final class PlaceholderChoices {

    /// The option that leaves the value out.
    static final String LEAVE_OUT = "Leave out";
    /// The option that keeps the value as it is written.
    static final String KEEP = "Keep as written";
    /// The option that writes the value as null, in JSON.
    static final String NULL = "Null";
    /// The option that writes the value as an empty string, in JSON.
    static final String EMPTY = "Empty string";
    /// The options for event-logging XML, the recommended one first.
    static final List<String> OPTIONS = List.of(LEAVE_OUT, KEEP);
    /// The options for JSON (a discovery index's documents), the recommended one first, each with what it is kept
    /// as (Discovery#STAND_IN_TREATMENTS).
    static final Map<String, String> JSON_OPTIONS = jsonOptions();
    /// The options for an index of Events, the recommended one first, each with what it is kept as. No empty
    /// string: an ip, number or date field refuses one, as it refuses '-'.
    static final Map<String, String> EVENTS_OPTIONS = eventsOptions();
    /// The form's message.
    static final String INTRO = "Some fields in the sample hold values that look like stand-ins for 'none'. Leave "
                                + "each out of the events, as if the field were empty, or keep it as written?";
    /// The form's message, for JSON documents.
    static final String JSON_INTRO = "Some fields in the raw data hold values that look like stand-ins for 'none'. "
                                     + "Index each as if the field were missing (leave it out), as null, as an "
                                     + "empty string, or as written?";
    /// The form's message, for an index of Events.
    static final String EVENTS_INTRO = "Some of the index's fields hold values in the Events that look like "
                                       + "stand-ins for 'none'. Index each as if the field were missing (leave it "
                                       + "out), as null, or as written?";
    // The most fields named in a question.
    private static final int MAX_FIELDS_NAMED = 4;

    private PlaceholderChoices() {
        // Static only.
    }

    /// @param found A stand-in found.
    /// @return The question put to the user about it.
    static String question(final Placeholders.Found found) {
        final List<String> fields = found.fields();
        final String named = String.join(", ", fields.subList(0, Math.min(MAX_FIELDS_NAMED, fields.size())))
                             + (fields.size() > MAX_FIELDS_NAMED
                ? " and " + (fields.size() - MAX_FIELDS_NAMED) + " more"
                : "");
        return "'" + found.value() + "' in " + named + " (" + found.records() + " values"
               + (found.kind() != null
                ? "; the others are " + found.kind()
                : "") + ")";
    }

    /// @param found   The stand-ins found.
    /// @param options The options of each.
    /// @return The questions put to the user, by value.
    static Map<String, Consent.Question> questions(final List<Placeholders.Found> found, final List<String> options) {
        final Map<String, Consent.Question> out = new LinkedHashMap<>();
        found.forEach(each -> out.put(each.value(), new Consent.Question(question(each), options)));
        return out;
    }

    /// @param mapping  The mapping.
    /// @param leaveOut The values the user chose to leave out.
    /// @return The mapping with them in its nil_values.
    static TranslationMapping leaveOut(final TranslationMapping mapping, final List<String> leaveOut) {
        final List<String> nil = new ArrayList<>(mapping.nilValues() == null
                ? List.of()
                : mapping.nilValues());
        leaveOut.stream().filter(value -> !nil.contains(value)).forEach(nil::add);
        final Map<String, Object> json = new LinkedHashMap<>(mapping.toJson(DumpMode.COMPACT));
        json.put("nil_values", nil);
        return TranslationMapping.fromJson(json);
    }

    // The JSON options, in order.
    private static Map<String, String> jsonOptions() {
        final Map<String, String> options = new LinkedHashMap<>();
        options.put(LEAVE_OUT, Discovery.LEAVE_OUT);
        options.put(NULL, Discovery.NULL);
        options.put(EMPTY, Discovery.EMPTY);
        options.put(KEEP, Discovery.KEEP);
        return Collections.unmodifiableMap(options);
    }

    // The options for an index of Events, in order.
    private static Map<String, String> eventsOptions() {
        final Map<String, String> options = new LinkedHashMap<>();
        options.put(LEAVE_OUT, Discovery.LEAVE_OUT);
        options.put(NULL, Discovery.NULL);
        options.put(KEEP, Discovery.KEEP);
        return Collections.unmodifiableMap(options);
    }

    /// @param found The stand-ins found.
    /// @return What the agent is told of them, without a form to ask the user in.
    static List<String> warnings(final List<Placeholders.Found> found) {
        return found.stream()
                .map(each -> "placeholder " + question(each) + " looks like a stand-in for none: ask the user "
                             + "whether to leave it out (nil_values ['" + each.value() + "']) or keep it as written")
                .toList();
    }
}
