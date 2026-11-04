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
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.draft.DraftMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// Where the time of records that give none, or the date of times that have none, comes from: the user's choice,
/// asked in a form (or given as `time_source` / `date_source`), never assumed. The stream's received or created time
/// is an approximation of when its events happened, so it is offered, not defaulted; a date in the file's name is
/// offered when the sample streams' `RemoteFile` holds one.
///
/// A date joined with each record's time of day is rolled over midnight (`rollover`), as a log's lines cross it.
final class TimeSources {

    /// The question put when records give no time.
    static final String TIME_QUESTION = "Some records have no time of their own. What should their Events' time be?";
    /// The question put when records' times have no date.
    static final String DATE_QUESTION = "Some records' times have no date. Where does their date come from?";
    /// The argument giving the answer to [#TIME_QUESTION]: `received`, `created` or `none`.
    static final String TIME_SOURCE_ARG = "time_source";
    /// The argument giving the answer to [#DATE_QUESTION]: `received`, `created`, `file_name` or `none`.
    static final String DATE_SOURCE_ARG = "date_source";

    private static final String TIME_CREATED = "EventTime/TimeCreated";
    private static final String RECEIVED = "received";
    private static final String CREATED = "created";
    private static final String FILE_NAME = "file_name";
    private static final String NONE = "none";
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Map<String, String> STREAM_TIMES = Map.of(RECEIVED, "ReceivedTime", CREATED,
            "CreatedTime");

    private TimeSources() {
        // Static only.
    }

    /// Asks where missing times and dates come from, and writes the answers into the draft's rules.
    ///
    /// @param call      The draft's call (its `time_source` and `date_source`).
    /// @param context   The call's context.
    /// @param consent   Asks the user in a form.
    /// @param action    The tool asking.
    /// @param result    The draft's result (its `dateless_times` and `notes`).
    /// @param mapping   The draft's mapping, changed in place.
    /// @param fileNames The sample streams' `RemoteFile` headers.
    @SuppressWarnings("unchecked")
    static void apply(final ToolCall call,
                      final ToolCallContext context,
                      final Consent consent,
                      final String action,
                      final Map<String, Object> result,
                      final Map<String, Object> mapping,
                      final List<String> fileNames) {
        final List<Object> notes = new ArrayList<>(JsonValues.listOrEmpty(result.get("notes")));
        final Map<String, Object> dateless = result.get(DraftMapping.DATELESS_TIMES) instanceof final Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        final List<Map<String, Object>> datelessRules = dateless.isEmpty()
                ? List.of()
                : rulesOf(mapping, String.valueOf(dateless.get("variant_field")),
                        ((Map<String, String>) dateless.get("variants")).keySet().stream().toList());
        if (!datelessRules.isEmpty()) {
            dates(call, context, consent, action, dateless, datelessRules, fileNames, notes);
        }
        final List<Map<String, Object>> timeless = new ArrayList<>(withoutTime(mapping));
        timeless.removeAll(datelessRules);
        if (!timeless.isEmpty()) {
            times(call, context, consent, action, mapping, timeless, notes);
        }
        result.put("notes", notes);
    }

    // The date for times that have none: joined with each, rolled over midnight.
    @SuppressWarnings("unchecked")
    private static void dates(final ToolCall call,
                              final ToolCallContext context,
                              final Consent consent,
                              final String action,
                              final Map<String, Object> dateless,
                              final List<Map<String, Object>> rules,
                              final List<String> fileNames,
                              final List<Object> notes) {
        final Optional<String> named = fileNames.stream().filter(name -> ISO_DATE.matcher(name).find()).findFirst();
        final Map<String, String> answers = new LinkedHashMap<>();
        answers.put("The date Stroom received the stream", RECEIVED);
        answers.put("The date the stream was created in Stroom", CREATED);
        named.ifPresent(name -> answers.put("The date in the file's name (e.g. " + name + ")", FILE_NAME));
        answers.put("Leave it: I'll say where", NONE);
        final String chosen = chosen(call, context, consent, action, DATE_SOURCE_ARG, DATE_QUESTION, answers);
        if (chosen == null || NONE.equals(chosen)) {
            notes.add("The times of " + names(rules) + " have no date and no header gives it: ask the user where it "
                      + "comes from (the date Stroom received or created the stream" + (named.isPresent()
                    ? ", the date in the file's name"
                    : "") + "), and draft again with date_source=received|created" + (named.isPresent()
                    ? "|file_name"
                    : "") + ", or map EventTime/TimeCreated yourself.");
            return;
        }
        final String date = switch (chosen) {
            case FILE_NAME -> "replace(stroom:meta('RemoteFile'), '^.*?(\\d{4}-\\d{2}-\\d{2}).*$', '$1')";
            default -> "substring(stroom:meta('" + STREAM_TIMES.get(chosen) + "'), 1, 10)";
        };
        final String field = String.valueOf(dateless.get("field"));
        final Map<String, String> formats = (Map<String, String>) dateless.get("variants");
        for (final Map<String, Object> rule : rules) {
            final String format = formats.getOrDefault(variantOf(rule, String.valueOf(dateless.get(
                    "variant_field"))), "HH:mm:ss");
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", TIME_CREATED);
            entry.put("xpath", "concat(" + date + ", ' ', data[@name='" + field + "']/@value)");
            entry.put("time_format", "yyyy-MM-dd " + format);
            entry.put("timezone", "UTC");
            entry.put("rollover", true);
            setTime(rule, entry);
        }
        notes.add("The times of " + names(rules) + " are joined with " + switch (chosen) {
            case FILE_NAME -> "the date in the file's name (RemoteFile)";
            case CREATED -> "the date the stream was created in Stroom";
            default -> "the date Stroom received the stream";
        } + ", as the user chose, a day added past midnight (rollover).");
    }

    // The time of records that give none: the stream's received or created time, an approximation.
    @SuppressWarnings("unchecked")
    private static void times(final ToolCall call,
                              final ToolCallContext context,
                              final Consent consent,
                              final String action,
                              final Map<String, Object> mapping,
                              final List<Map<String, Object>> rules,
                              final List<Object> notes) {
        final Map<String, String> answers = new LinkedHashMap<>();
        answers.put("When Stroom received the stream (an approximation)", RECEIVED);
        answers.put("When the stream was created in Stroom (an approximation)", CREATED);
        answers.put("Leave it: I'll say which field", NONE);
        final String chosen = chosen(call, context, consent, action, TIME_SOURCE_ARG, TIME_QUESTION, answers);
        if (chosen == null || NONE.equals(chosen)) {
            notes.add(names(rules) + " write no EventTime/TimeCreated: ask the user what it should be (when Stroom "
                      + "received or created the stream, both approximations, or a field of their own), and draft "
                      + "again with time_source=received|created, or map it yourself. Never the processing time.");
            return;
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", TIME_CREATED);
        entry.put("meta", STREAM_TIMES.get(chosen));
        if (rules.size() == JsonValues.maps(mapping.get("events")).size()) {
            // Every rule's: once, in common.
            final List<Object> common = new ArrayList<>(JsonValues.listOrEmpty(mapping.get("common")));
            common.addFirst(entry);
            mapping.put("common", common);
        } else {
            rules.forEach(rule -> setTime(rule, new LinkedHashMap<>(entry)));
        }
        notes.add(names(rules) + " take the time " + (CREATED.equals(chosen)
                ? "the stream was created in Stroom"
                : "Stroom received the stream") + ", as the user chose: an approximation of when the events "
                  + "happened.");
    }

    // The answer given as the argument, else asked in a form; null when neither (no forms).
    private static String chosen(final ToolCall call,
                                 final ToolCallContext context,
                                 final Consent consent,
                                 final String action,
                                 final String argument,
                                 final String question,
                                 final Map<String, String> answers) {
        final Object given = ToolArgs.raw(call, argument);
        if (given != null) {
            final String value = String.valueOf(given).strip();
            if (!answers.containsValue(value)) {
                throw new ToolRefusal(argument + " is one of " + String.join(", ", answers.values()));
            }
            return value;
        }
        return consent.choose(call, context, action, question, List.copyOf(answers.keySet()), null)
                .map(answers::get)
                .orElse(null);
    }

    // The rules that write no TimeCreated, where common doesn't either.
    private static List<Map<String, Object>> withoutTime(final Map<String, Object> mapping) {
        if (writesTime(JsonValues.maps(mapping.get("common")))) {
            return List.of();
        }
        return JsonValues.maps(mapping.get("events")).stream()
                .filter(rule -> !writesTime(JsonValues.maps(rule.get("fields"))))
                .toList();
    }

    private static boolean writesTime(final List<Map<String, Object>> entries) {
        return entries.stream().anyMatch(entry -> TIME_CREATED.equals(entry.get("path")));
    }

    // The rules for records of these variants (when the variant field equals one of them).
    private static List<Map<String, Object>> rulesOf(final Map<String, Object> mapping,
                                                     final String field,
                                                     final List<String> variants) {
        return JsonValues.maps(mapping.get("events")).stream()
                .filter(rule -> variants.contains(variantOf(rule, field)))
                .toList();
    }

    private static String variantOf(final Map<String, Object> rule, final String field) {
        for (final Map<String, Object> condition : JsonValues.maps(rule.get("when"))) {
            if (field.equals(condition.get("field")) && condition.get("equals") != null) {
                return String.valueOf(condition.get("equals"));
            }
        }
        return null;
    }

    // The rule's TimeCreated, first among its fields, in place of any it had.
    @SuppressWarnings("unchecked")
    private static void setTime(final Map<String, Object> rule, final Map<String, Object> entry) {
        final List<Object> fields = new ArrayList<>((List<Object>) rule.get("fields"));
        fields.removeIf(field -> field instanceof final Map<?, ?> map && TIME_CREATED.equals(map.get("path")));
        fields.addFirst(entry);
        rule.put("fields", fields);
    }

    private static String names(final List<Map<String, Object>> rules) {
        return "rule" + (rules.size() == 1
                ? " "
                : "s ") + String.join(", ", rules.stream().map(rule -> "'" + rule.get("name") + "'").toList());
    }
}
