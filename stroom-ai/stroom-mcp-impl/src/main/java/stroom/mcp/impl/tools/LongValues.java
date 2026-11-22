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

import stroom.ai.impl.tools.AcceptedErrors;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.JsonStringValues;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// The user's choice for JSON string values longer than Stroom's JSONParser keeps (its `stringTruncateLength`,
/// 10,000 characters by default; it cuts a longer one there and logs a Warning): kept whole, or cut. A build's
/// choice is asked by `draft_translation_mapping` in a short form when its sample holds such values (or given as
/// `long_values`), and kept in the build's record ([BuildRecords#rememberLongValues]), so the tools follow it:
///
/// * **whole**: `create_pipeline` (and `update_pipeline`, on a pipeline made before) sets the build's JSON parser's
///   `stringTruncateLength` past the longest value ([#parserLimit]);
/// * **cut**: the parser's Warning is benign in triage for the build's pipelines ([#accepted]), so a step can be
///   clean and processing go ahead, and `write_documentation` records it with the errors the user accepted.
///
/// With no choice recorded, the Warning stays for review, its reason naming `long_values`. Seen in user test chat
/// 182: the user said "Cut them" in the chat, nothing kept it, and `create_processor_filter` was refused for want of
/// a clean step.
final class LongValues {

    /// The draft's argument giving the user's choice: [#WHOLE] or [#CUT].
    static final String ARG = "long_values";
    /// Keep the values whole: the JSON parser's limit raised past them.
    static final String WHOLE = "whole";
    /// Cut the values at the JSON parser's limit.
    static final String CUT = "cut";
    /// The JSON parser's property that cuts long values.
    static final String PARSER_PROPERTY = "stringTruncateLength";
    /// The start of the JSON parser's Warning when it cuts a value.
    static final String CUT_WARNING = "String value truncated to length";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LongValues.class);

    private static final String CHOICE = "choice";
    private static final String LIMIT = "limit";
    private static final String LONGEST = "longest";
    private static final String FIELDS = "fields";
    private static final String HOW = "how";
    private static final String RECORDED = "recorded";
    private static final String JSON_PARSER = "JSONParser";

    private LongValues() {
        // Static only.
    }

    /// Settles the user's choice for the draft's long values and says it in the draft's notes: given as
    /// `long_values`, else the build's recorded choice, else asked in a short form (only with a build, where the
    /// answer is kept). The choice is recorded in the build's record; once made, the notes no longer say to ask.
    ///
    /// @param call    The draft's call (its `long_values` and `user_changed`).
    /// @param context The call's context.
    /// @param consent Asks the user in a form.
    /// @param records The build records.
    /// @param build   The draft's build, or null.
    /// @param result  The draft's result (its `long_strings` and `notes`), changed in place: `long_values` says the
    ///                choice.
    /// @throws ToolRefusal if `long_values` isn't one of the choices, is given without a build, or differs from the
    ///                     choice recorded without `user_changed`.
    static void apply(final ToolCall call,
                      final ToolCallContext context,
                      final Consent consent,
                      final BuildRecords records,
                      final String build,
                      final Map<String, Object> result) {
        final String given = given(call);
        final List<Map<String, Object>> longest = JsonStringValues.longStringsOf(result);
        if (given != null && NullSafe.isBlankString(build)) {
            throw new ToolRefusal("long_values is kept with a build: give build= too");
        }
        if (NullSafe.isBlankString(build) || (longest.isEmpty() && given == null)) {
            return;
        }
        final Map<String, Object> recorded = recorded(records, build).orElse(null);
        final String recordedChoice = recorded == null
                ? null
                : String.valueOf(recorded.get(CHOICE));
        if (given != null && recordedChoice != null && !given.equals(recordedChoice)
            && !ToolArgs.flag(call, DraftTranslationMappingTool.USER_CHANGED_ARG)) {
            throw new ToolRefusal("The user chose to " + said(recordedChoice) + " for this build (recorded "
                                  + recorded.get(RECORDED) + "): leave long_values out to follow it, "
                                  + "or, if the user has changed their mind, call again with user_changed=true and "
                                  + "long_values=" + given + ".");
        }
        String how = given != null
                ? "given"
                : null;
        String choice = given != null
                ? given
                : recordedChoice;
        if (choice == null) {
            choice = asked(call, context, consent, longest).orElse(null);
            how = "form";
        }
        if (choice == null) {
            // No form here: the agent asks, and records the answer with long_values.
            replaceNote(result, JsonStringValues.askNote(longest));
            return;
        }
        // The longest value seen, in this sample or one drafted before.
        final int recordedLength = recorded != null && recorded.get(LONGEST) instanceof final Number number
                ? number.intValue()
                : 0;
        final int length = Math.max(JsonStringValues.longestLength(longest), recordedLength);
        final Integer limit = WHOLE.equals(choice) && length > JsonStringValues.DEFAULT_TRUNCATE_LENGTH
                ? JsonStringValues.wholeLimit(length)
                : null;
        if (recorded == null || !choice.equals(recordedChoice) || !sameLimit(recorded, limit)) {
            final Map<String, Object> kept = new LinkedHashMap<>();
            kept.put(CHOICE, choice);
            if (limit != null) {
                kept.put(LIMIT, limit);
            }
            if (length > 0) {
                kept.put(LONGEST, length);
            }
            kept.put(FIELDS, longest.isEmpty() && recorded != null
                    ? recorded.getOrDefault(FIELDS, List.of())
                    : longest.stream().map(entry -> String.valueOf(entry.get("field"))).distinct().toList());
            kept.put(HOW, how != null
                    ? how
                    : recorded.getOrDefault(HOW, "form"));
            kept.put(RECORDED, LocalDate.now(ZoneOffset.UTC).toString());
            try {
                records.rememberLongValues(build, kept);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Couldn't keep the long values choice in build {}'s record: {}",
                        build, e.getMessage()), e);
            }
        }
        final Map<String, Object> chosen = new LinkedHashMap<>();
        chosen.put(CHOICE, choice);
        if (limit != null) {
            chosen.put("json_parser_" + PARSER_PROPERTY, limit);
        }
        chosen.put("kept", "in the build's record: step_sample, processing and write_documentation follow it");
        result.put(ARG, chosen);
        if (!longest.isEmpty()) {
            replaceNote(result, WHOLE.equals(choice)
                    ? JsonStringValues.wholeNote(longest, limit)
                    : JsonStringValues.cutNote(longest));
        }
    }

    /// @param records The build records.
    /// @param build   A build, or null.
    /// @return The choice recorded for the build: `choice`, `limit` (kept whole), `longest`, `fields`, `how` and
    /// `recorded` (its date); empty when none is, or it can't be read.
    static Optional<Map<String, Object>> recorded(final BuildRecords records, final String build) {
        try {
            return records.longValues(build)
                    .filter(kept -> WHOLE.equals(kept.get(CHOICE)) || CUT.equals(kept.get(CHOICE)));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Couldn't read build {}'s long values choice: {}", build,
                    e.getMessage()));
            return Optional.empty();
        }
    }

    /// The JSON parser's `stringTruncateLength` for a build whose user chose to keep long values whole, where the
    /// pipeline's parser keeps less: the parser's limit raised past the longest value.
    ///
    /// @param records  The build records.
    /// @param build    The pipeline's build, or null.
    /// @param elements The pipeline's elements, id to type.
    /// @param have     The pipeline's properties set (its own and inherited, and those about to be set), each by
    ///                 its element and name.
    /// @return The property to set; empty when the user chose no such thing, the pipeline has no JSON parser, or
    /// its limit is high enough already.
    static Optional<PropertyValue> parserLimit(final BuildRecords records,
                                               final String build,
                                               final Map<String, String> elements,
                                               final Map<ElementProperty, Object> have) {
        final Optional<Map<String, Object>> chosen = recorded(records, build)
                .filter(kept -> WHOLE.equals(kept.get(CHOICE)) && kept.get(LIMIT) instanceof Number);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        final long limit = ((Number) chosen.get().get(LIMIT)).longValue();
        return elements.entrySet().stream()
                .filter(element -> JSON_PARSER.equals(element.getValue()))
                .map(Map.Entry::getKey)
                .filter(parser -> lower(have.get(new ElementProperty(parser, PARSER_PROPERTY)), limit))
                .findFirst()
                .map(parser -> PropertyValue.plain(parser, PARSER_PROPERTY, limit));
    }

    /// The JSON parser's Warning as an error the user accepted, for a build whose user chose to cut long values:
    /// triage then classes it benign for the build's pipelines, saying why, so a step can be clean.
    ///
    /// @param records The build records.
    /// @param build   The pipeline's build, or null.
    /// @return The entry; empty when the user chose no such thing (or the pipeline is in no build).
    static Optional<AcceptedErrors.Entry> accepted(final BuildRecords records, final String build) {
        return recorded(records, build)
                .filter(kept -> CUT.equals(kept.get(CHOICE)))
                .map(kept -> AcceptedErrors.entry(null, CUT_WARNING + " " + JsonStringValues.DEFAULT_TRUNCATE_LENGTH
                                                        + ", original length: " + kept.getOrDefault(LONGEST, "N"),
                        "the user chose to cut string values longer than the JSON parser keeps (its "
                        + PARSER_PROPERTY + ")" + fieldsSaid(kept) + ", recorded for build '" + build
                        + "' by draft_translation_mapping (long_values=cut): their Events hold each value's first "
                        + JsonStringValues.DEFAULT_TRUNCATE_LENGTH + " characters",
                        String.valueOf(kept.get(RECORDED)), CUT_WARNING + " *"));
    }

    /// The JSON parser's Warning that it cut a value, explained for a build whose user chose to keep long values
    /// whole: the parser still cut one, so it is still for review, but the reason says the build keeps them whole,
    /// the limit recorded for it (if any), the longest value seen, and how to raise the pipeline's limit.
    ///
    /// @param records The build records.
    /// @param build   The pipeline's build, or null.
    /// @return The entry; empty when the user chose no such thing (or the pipeline is in no build).
    static Optional<AcceptedErrors.Entry> stillCut(final BuildRecords records, final String build) {
        return recorded(records, build)
                .filter(kept -> WHOLE.equals(kept.get(CHOICE)))
                .map(kept -> AcceptedErrors.entry(null, CUT_WARNING + " " + JsonStringValues.DEFAULT_TRUNCATE_LENGTH
                                                        + ", original length: " + kept.getOrDefault(LONGEST, "N"),
                        stillCutReason(build, kept), String.valueOf(kept.get(RECORDED)), CUT_WARNING + " *"));
    }

    // Why the parser still cuts a value the user chose to keep whole, and what to do.
    private static String stillCutReason(final String build, final Map<String, Object> kept) {
        final String limit = kept.get(LIMIT) instanceof final Number number
                ? "the limit recorded for it is " + grouped(number.intValue()) + " characters (create_pipeline sets "
                  + "it)"
                : "no limit is recorded for it (the longest value seen was within the parser's default)";
        final String longest = kept.get(LONGEST) instanceof final Number number
                ? ", and the longest value seen in its samples was " + grouped(number.intValue()) + " characters"
                : "";
        return "Build '" + build + "' keeps string values longer than the JSON parser's default limit whole, by the "
               + "user's choice (draft_translation_mapping long_values=whole, recorded " + kept.get(RECORDED) + "): "
               + limit + longest + ". The parser still cut this value: it is longer than the pipeline's "
               + PARSER_PROPERTY + " (its message gives both lengths), or that limit was never set on this pipeline "
               + "(one made before the choice, or set back). Raise it past this value's original length with "
               + "update_pipeline set_properties [{element: <the JSON parser>, name: " + PARSER_PROPERTY + ", value: "
               + "<a size>}], then step and process the streams again. draft_translation_mapping raises the "
               + "recorded limit when its sample holds a longer value, and update_pipeline then sets it.";
    }

    /// @param records The build records.
    /// @param build   A build, or null.
    /// @return What the documentation says of the build's long values, for its Open items; empty when no choice is
    /// recorded.
    static Optional<String> documented(final BuildRecords records, final String build) {
        return recorded(records, build).map(kept -> CUT.equals(kept.get(CHOICE))
                ? "String values longer than the JSON parser keeps" + fieldsSaid(kept) + " are cut at "
                  + JsonStringValues.DEFAULT_TRUNCATE_LENGTH + " characters, by the user's choice (recorded "
                  + kept.get(RECORDED) + "); the parser's Warning is recorded under Errors as accepted. Say so in "
                  + "Open items."
                : "String values longer than the JSON parser's default limit" + fieldsSaid(kept) + " are kept whole, "
                  + "by the user's choice (recorded " + kept.get(RECORDED) + "), its " + PARSER_PROPERTY + " set to "
                  + kept.getOrDefault(LIMIT, "past the longest value") + ".");
    }

    // The choice given as long_values, or null.
    private static String given(final ToolCall call) {
        final Object raw = ToolArgs.raw(call, ARG);
        if (raw == null) {
            return null;
        }
        final String value = String.valueOf(raw).strip().toLowerCase(Locale.ENGLISH);
        if (!WHOLE.equals(value) && !CUT.equals(value)) {
            throw new ToolRefusal("long_values is whole (raise the JSON parser's stringTruncateLength past the "
                                  + "longest value) or cut (the parser cuts each at its limit), as the user chose");
        }
        return value;
    }

    // The user's answer in a short form; empty when no form can be shown.
    private static Optional<String> asked(final ToolCall call,
                                          final ToolCallContext context,
                                          final Consent consent,
                                          final List<Map<String, Object>> longest) {
        final int length = JsonStringValues.longestLength(longest);
        final Map<String, String> answers = new LinkedHashMap<>();
        answers.put("Keep them whole (raise the JSON parser's limit to "
                    + grouped(JsonStringValues.wholeLimit(length)) + " characters)", WHOLE);
        answers.put("Cut them at " + grouped(JsonStringValues.DEFAULT_TRUNCATE_LENGTH) + " characters",
                CUT);
        final String fields = String.join(", ", longest.stream()
                .map(entry -> String.valueOf(entry.get("field")))
                .distinct()
                .toList());
        final String question = "Some values are longer than Stroom's JSON parser keeps ("
                                + grouped(JsonStringValues.DEFAULT_TRUNCATE_LENGTH)
                                + " characters): " + fields + ", the longest " + grouped(length)
                                + " characters. Keep them whole, or cut them?";
        return consent.choose(call, context, DraftTranslationMappingTool.NAME, question,
                        List.copyOf(answers.keySet()), "Values longer than the JSON parser keeps")
                .map(answers::get);
    }

    // The draft's note on long values replaced (or added, for an argument given with none in this sample).
    private static void replaceNote(final Map<String, Object> result, final String note) {
        final List<Object> notes = new ArrayList<>(JsonValues.listOrEmpty(result.get("notes")));
        final int at = indexOfLongNote(notes);
        if (at >= 0) {
            notes.set(at, note);
        } else {
            notes.add(note);
        }
        result.put("notes", notes);
    }

    private static int indexOfLongNote(final List<Object> notes) {
        for (int i = 0; i < notes.size(); i++) {
            if (JsonStringValues.isLongStringsNote(notes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameLimit(final Map<String, Object> recorded, final Integer limit) {
        final Object kept = recorded.get(LIMIT);
        return limit == null
                ? kept == null
                : kept instanceof final Number number && number.intValue() == limit;
    }

    // True when a limit set (a number, or its text) is below the one wanted, or none is set.
    private static boolean lower(final Object set, final long wanted) {
        if (set == null) {
            return true;
        }
        try {
            return Long.parseLong(String.valueOf(set).strip()) < wanted;
        } catch (final NumberFormatException e) {
            return true;
        }
    }

    private static String fieldsSaid(final Map<String, Object> kept) {
        final List<Object> fields = JsonValues.listOrEmpty(kept.get(FIELDS));
        return fields.isEmpty()
                ? ""
                : " (" + String.join(", ", fields.stream().map(String::valueOf).toList()) + ")";
    }

    // A number with its thousands grouped, e.g. 10,000.
    private static String grouped(final int number) {
        return String.format(Locale.ENGLISH, "%,d", number);
    }

    private static String said(final String choice) {
        return WHOLE.equals(choice)
                ? "keep long values whole"
                : "cut long values";
    }
}
