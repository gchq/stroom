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

package stroom.ai.impl.tools.sample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// JSON string values that Stroom's JSONParser doesn't pass on as they are, found in a sample's records before
/// anything is processed:
///
/// * **Long values.** The parser cuts a string longer than its `stringTruncateLength` (10,000 characters by
///   default) at that length and logs a Warning ("String value truncated to length N, original length: M"); its
///   `maxStringLength` (100,000,000 by default) is a different limit, past which the parser logs an Error. Seen: an
///   agent that wasn't told set `maxStringLength` to 10,000 and every long body became an Error.
/// * **NUL characters** (U+0000, escaped in the JSON). The parser decodes the escape into the character itself, which
///   XML can't hold, and Stroom's XML writer takes it for its escape switch (its `preventEscapeSwitching` is false
///   by default): the text after it is written unescaped, so an `&`, `<` or control character in it makes the
///   Events stream not well-formed. Seen: mail bodies holding `&nbsp;` and a form feed after a NUL.
public final class JsonStringValues {

    /// The JSONParser's default `stringTruncateLength`.
    public static final int DEFAULT_TRUNCATE_LENGTH = 10_000;
    /// The profile's list of the longest string values over the limit.
    public static final String LONG_STRINGS = "long_strings";
    /// What to do about them.
    public static final String LONG_STRINGS_NOTE = "long_strings_note";
    /// The profile's list of fields whose values hold a NUL character.
    public static final String NUL_STRINGS = "nul_strings";
    /// What to do about them.
    public static final String NUL_STRINGS_NOTE = "nul_strings_note";

    static final int MOST_REPORTED = 5;
    // What an event left out of a sample is called where its field or record is said.
    static final String LEFT_OUT = "an event left out of the sample";
    // The formats Stroom's JSONParser reads.
    private static final Set<Object> JSON_FORMATS = Set.of(SampleProfiler.JSON_ARRAY, SampleProfiler.JSON_LINES,
            SampleProfiler.JSON_OBJECT);
    private static final int MOST_DEPTH = 64;
    private static final char NUL = 0;

    private JsonStringValues() {
        // Static only.
    }

    /// The longest string values over the limit, longest first, each once a field (its longest).
    ///
    /// @param records The records, as read from JSON (maps, lists and plain values).
    /// @param limit   The length a value may be without being cut.
    /// @return Up to [#MOST_REPORTED] of `{field, length, record}` (`record` counts from 1; `field` is the dotted
    /// path, `[]` for an array's items); empty when none is longer than the limit.
    public static List<Map<String, Object>> longStrings(final List<?> records, final int limit) {
        final Map<String, Map<String, Object>> longestByField = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            final int recordNo = i + 1;
            walk(records.get(i), "", 0, (field, text) -> {
                if (text.length() > limit) {
                    final Map<String, Object> kept = longestByField.get(field);
                    if (kept == null || (int) kept.get("length") < text.length()) {
                        longestByField.put(field, SampleProfiler.map("field", field, "length", text.length(),
                                "record", recordNo));
                    }
                }
            });
        }
        final List<Map<String, Object>> found = new ArrayList<>(longestByField.values());
        found.sort(Comparator.<Map<String, Object>>comparingInt(m -> (int) m.get("length")).reversed());
        return found.subList(0, Math.min(MOST_REPORTED, found.size()));
    }

    /// The fields whose string values hold a NUL character.
    ///
    /// @param records The records, as read from JSON.
    /// @return Up to [#MOST_REPORTED] dotted field paths, in the order first seen; empty when none.
    public static List<String> nulStrings(final List<?> records) {
        final Set<String> fields = new LinkedHashSet<>();
        for (final Object record : records) {
            walk(record, "", 0, (field, text) -> {
                if (text.indexOf(NUL) >= 0) {
                    fields.add(field);
                }
            });
        }
        return List.copyOf(fields).subList(0, Math.min(MOST_REPORTED, fields.size()));
    }

    /// Adds to a JSON sample's profile what Stroom's JSONParser would cut or the XML writer would leave unescaped:
    /// [#LONG_STRINGS] and [#LONG_STRINGS_NOTE], [#NUL_STRINGS] and [#NUL_STRINGS_NOTE]; nothing when there is
    /// neither.
    ///
    /// @param profile The profile, changed.
    /// @param records The records, as read from JSON.
    public static void addTo(final Map<String, Object> profile, final List<?> records) {
        final List<Map<String, Object>> longest = longStrings(records, DEFAULT_TRUNCATE_LENGTH);
        if (!longest.isEmpty()) {
            profile.put(LONG_STRINGS, longest);
            profile.put(LONG_STRINGS_NOTE, longStringsNote(longest, DEFAULT_TRUNCATE_LENGTH));
        }
        final List<String> nul = nulStrings(records);
        if (!nul.isEmpty()) {
            profile.put(NUL_STRINGS, nul);
            profile.put(NUL_STRINGS_NOTE, nulStringsNote(nul));
        }
    }

    /// Adds to a JSON sample's profile the longest string value of events left out of the sample as too long to
    /// sample ([PartSampler]), when it is longer than the JSONParser keeps: the warning that Stroom would cut it is
    /// not lost because its event wasn't sampled. Nothing is added to a profile of another format.
    ///
    /// @param profile The profile, changed.
    /// @param where   Where the events were left out, e.g. `stream 7 part 3`.
    /// @param skipped The events left out there.
    public static void addSkipped(final Map<String, Object> profile,
                                  final String where,
                                  final PartSampler.SkippedEvents skipped) {
        if (skipped.longestText() <= DEFAULT_TRUNCATE_LENGTH || !JSON_FORMATS.contains(profile.get("format"))) {
            return;
        }
        final List<Map<String, Object>> longest = new ArrayList<>();
        if (profile.get(LONG_STRINGS) instanceof final List<?> list) {
            list.forEach(item -> {
                @SuppressWarnings("unchecked") final Map<String, Object> entry = (Map<String, Object>) item;
                longest.add(entry);
            });
        }
        longest.add(SampleProfiler.map("field", skipped.path() != null
                        ? skipped.path()
                        : LEFT_OUT,
                "length", (int) Math.min(Integer.MAX_VALUE, skipped.longestText()),
                "record", LEFT_OUT + " (" + where + ")"));
        longest.sort(Comparator.<Map<String, Object>>comparingInt(m -> (int) m.get("length")).reversed());
        final List<Map<String, Object>> kept = List.copyOf(longest.subList(0, Math.min(MOST_REPORTED,
                longest.size())));
        profile.put(LONG_STRINGS, kept);
        profile.put(LONG_STRINGS_NOTE, longStringsNote(kept, DEFAULT_TRUNCATE_LENGTH));
    }

    /// @param longest The longest values, as [#longStrings] gives them.
    /// @param limit   The parser's `stringTruncateLength`.
    /// @return What the parser does with them and the user's choice.
    static String longStringsNote(final List<Map<String, Object>> longest, final int limit) {
        final Map<String, Object> first = longest.getFirst();
        return "String values longer than " + limit + " characters (the longest: '" + first.get("field") + "', "
               + first.get("length") + " characters, record " + first.get("record") + "). Stroom's JSONParser "
               + "cuts each at " + limit + " characters (its stringTruncateLength) and logs a Warning. Ask the "
               + "user which they want: the field whole (raise jsonParser.stringTruncateLength on the pipeline "
               + "past the longest value, with update_pipeline set_properties), or cut (cap it in the mapping, "
               + "e.g. an xpath with substring(..., 1, 4000)). Leave maxStringLength alone: it is a separate limit "
               + "that logs an Error, and lowering it turns every long value into one.";
    }

    /// @param fields The fields holding a NUL character.
    /// @return What the XML writer does with it and what to do.
    static String nulStringsNote(final List<String> fields) {
        return "Values of " + String.join(", ", fields) + " hold a NUL character (\\u0000 in the JSON). Stroom's "
               + "JSONParser passes it on, and the XML writer takes it to switch output escaping off: the text "
               + "after it is written unescaped, so an & or < in it makes the Events stream not well-formed. Map "
               + "these fields with transform strip_control, which takes control characters out.";
    }

    private static void walk(final Object value, final String path, final int depth, final StringVisitor visitor) {
        if (depth > MOST_DEPTH) {
            return;
        }
        if (value instanceof final Map<?, ?> map) {
            map.forEach((key, inner) -> walk(inner, JsonFieldNames.child(path, String.valueOf(key)), depth + 1,
                    visitor));
        } else if (value instanceof final List<?> list) {
            final String items = (path.isEmpty()
                    ? "(value)"
                    : path) + "[]";
            list.forEach(inner -> walk(inner, items, depth + 1, visitor));
        } else if (value instanceof final String text) {
            visitor.visit(path.isEmpty()
                    ? "(value)"
                    : path, text);
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    private interface StringVisitor {

        void visit(String field, String text);
    }
}
