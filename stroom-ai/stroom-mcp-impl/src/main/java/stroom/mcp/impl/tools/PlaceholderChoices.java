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

import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.sample.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Values a source writes for none (`-`, `N/A`, ...; [Placeholders]), put to the user, a question for each value
/// whatever fields hold it:
///
/// - build_translation_xslt, before the XSLT is saved: leave them out of the events (the mapping's nil_values), or
///   keep them as written ([#OPTIONS]);
/// - draft_index_mapping for a discovery index: index them as missing, null, an empty string, or as written
///   ([#JSON_OPTIONS]);
/// - draft_index_mapping for an index of Events: as missing, null, or as written ([#EVENTS_OPTIONS]).
///
/// Asked for by the user (seen: W3C logs' `-` for a missing address went on into an Elasticsearch ip field, which
/// refused the documents).
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
    /// The key of the question that covers the values not asked about one by one, when there are too many for one
    /// form. No value found is blank ([Placeholders#find]), so none has this key.
    static final String OTHERS = "";
    // The most fields named in a question.
    private static final int MAX_FIELDS_NAMED = 4;
    // The most questions in a form: Stroom shows no form of more fields (seen: a sample of ~150 fields held dozens of
    // stand-ins, and their form was declined as too large, so the XSLT could never be saved).
    private static final int MAX_QUESTIONS = ElicitationSchema.MAX_FIELDS;
    // The most of the other values named in the question that covers them.
    private static final int MAX_OTHERS_NAMED = 3;

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

    /// @param found   The stand-ins found, most records first.
    /// @param options The options of each.
    /// @return The questions put to the user, by value: one for each, unless there are more than a form can show,
    /// when the most common are asked about one by one and one more question ([#OTHERS]) covers the rest.
    static Map<String, Consent.Question> questions(final List<Placeholders.Found> found, final List<String> options) {
        final Map<String, Consent.Question> out = new LinkedHashMap<>();
        final int oneByOne = found.size() <= MAX_QUESTIONS
                ? found.size()
                : MAX_QUESTIONS - 1;
        found.subList(0, oneByOne).forEach(each -> out.put(each.value(), new Consent.Question(question(each),
                options)));
        if (oneByOne < found.size()) {
            out.put(OTHERS, new Consent.Question(others(found.subList(oneByOne, found.size())), options));
        }
        return out;
    }

    /// @param found  The stand-ins asked about, as given to [#questions].
    /// @param chosen The option picked for each question, by its key.
    /// @return The option picked for each value, by value: a value not asked about one by one has the option picked
    /// for the others. A value with no option picked is left out.
    static Map<String, String> byValue(final List<Placeholders.Found> found, final Map<String, String> chosen) {
        final Map<String, String> out = new LinkedHashMap<>();
        found.forEach(each -> {
            final String option = chosen.containsKey(each.value())
                    ? chosen.get(each.value())
                    : chosen.get(OTHERS);
            if (option != null) {
                out.put(each.value(), option);
            }
        });
        return out;
    }

    // The question that covers the values not asked about one by one.
    private static String others(final List<Placeholders.Found> rest) {
        final String named = rest.stream()
                .limit(MAX_OTHERS_NAMED)
                .map(each -> "'" + each.value() + "'")
                .collect(Collectors.joining(", "));
        return "The other " + rest.size() + " values (" + named + (rest.size() > MAX_OTHERS_NAMED
                ? ", ..."
                : "") + "), all the same way";
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
