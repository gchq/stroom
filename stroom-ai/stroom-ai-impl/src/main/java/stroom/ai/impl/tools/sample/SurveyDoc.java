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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolRefusal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The survey record: a Documentation document per surveyed feed, readable by people and by survey_feed (the
/// Stroom MCP server's `utils/surveydoc.py`, whose document format this keeps exactly, so a record the Python
/// server wrote reads here and the other way round).
///
/// The document shows which kinds of event the feed holds (counts, shares, streams, examples with where they are),
/// and ends with a fenced JSON block that survey_feed reads back to carry on where the last survey stopped: which
/// streams were read, which shapes are known, and which kinds the user chose to leave untranslated
/// (set_shape_handling). Example records are stored as they are; who can read them is down to the permissions on
/// the folder the document lives in, as for the feed's data.
///
/// The state is JSON: `feed`, `format`, `time_range` (`oldest`, `newest`), `streams_read`, `saturated`, `surveys`
/// (each `at`, `streams`, `records`, `new_shapes`), `shapes` (by signature: `count`, `streams`, `examples` with
/// `text` and `location`, `first_seen`) and `handling` (by signature: `handling`, `reason`, `at`).
public final class SurveyDoc {

    /// The fenced block holding the state.
    public static final Pattern STATE_FENCE = PyText.re("```json survey-state\\n(.*?)\\n```", Pattern.DOTALL);
    /// The most characters of an example record kept.
    public static final int MAX_EXAMPLE_CHARS = 4000;

    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Pattern WHITESPACE = PyText.re("\\s+");

    private SurveyDoc() {
        // Static methods only.
    }

    /// @param feed A feed's name.
    /// @return The name of its survey document.
    public static String docName(final String feed) {
        return feed + " - Survey";
    }

    /// @param instant A time.
    /// @return It as the survey record writes times, e.g. `2026-10-22T09:30:00Z`.
    public static String at(final Instant instant) {
        return AT.format(instant);
    }

    /// @param markdown A survey document's text, or null.
    /// @return The survey state its fenced block holds, or null if it has none (or it isn't JSON).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readState(final String markdown) {
        final Matcher match = STATE_FENCE.matcher(markdown == null
                ? ""
                : markdown);
        if (!match.find()) {
            return null;
        }
        try {
            final Object state = PyJson.loads(match.group(1));
            return state instanceof Map
                    ? (Map<String, Object>) state
                    : null;
        } catch (final PyJson.JsonDecodeException e) {
            return null;
        }
    }

    /// Shape signatures for the names given: exact signatures, or text found in exactly one signature.
    ///
    /// @param state A survey state.
    /// @param names Signatures, or text in them, e.g. `loggerName=org.jgroups`.
    /// @return The signatures, each once, in order.
    /// @throws ToolRefusal for a name in no signature or in several.
    public static List<String> resolve(final Map<String, Object> state, final List<String> names) {
        final Map<String, Object> shapes = shapes(state);
        final Set<String> out = new LinkedHashSet<>();
        for (final String name : names) {
            if (shapes.containsKey(name)) {
                out.add(name);
                continue;
            }
            final List<String> hits = shapes.keySet().stream().filter(signature -> signature.contains(name)).toList();
            if (hits.size() != 1) {
                throw new ToolRefusal("'" + name + "' matches " + hits.size() + " kinds of event in the survey; "
                                      + "give the exact signature");
            }
            out.add(hits.getFirst());
        }
        return new ArrayList<>(out);
    }

    /// Records that kinds of event are left untranslated (drop), or are translated again (translate).
    ///
    /// @param state      A survey state, changed in place.
    /// @param signatures The kinds' signatures.
    /// @param handling   `drop` or `translate`.
    /// @param reason     Why, in the user's words.
    /// @param at         When, as [#at(Instant)] writes it.
    @SuppressWarnings("unchecked")
    public static void setHandling(final Map<String, Object> state,
                                   final List<String> signatures,
                                   final String handling,
                                   final String reason,
                                   final String at) {
        final Map<String, Object> kept = (Map<String, Object>) state.computeIfAbsent("handling",
                k -> new LinkedHashMap<String, Object>());
        for (final String signature : signatures) {
            if ("drop".equals(handling)) {
                kept.put(signature, SampleProfiler.map("handling", "drop", "reason", reason, "at", at));
            } else {
                kept.remove(signature);
            }
        }
    }

    /// @param state A survey state, or null.
    /// @return Signature to reason, for the kinds of event the user chose to leave untranslated.
    public static Map<String, String> dropped(final Map<String, Object> state) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (state == null || !(state.get("handling") instanceof final Map<?, ?> handling)) {
            return out;
        }
        handling.forEach((signature, value) -> {
            if (value instanceof final Map<?, ?> entry && "drop".equals(entry.get("handling"))) {
                out.put((String) signature, PyText.str(entry.get("reason")));
            }
        });
        return out;
    }

    /// Adds one survey_feed call to the record, now: counts add up, examples are kept up to the limit.
    ///
    /// @param state            The record's state so far, changed in place, or null for a new record.
    /// @param result           The call's result: `feed`, `format`, `time_range`, `streams_read`, `saturated`,
    ///                         `records_read` and `new_shapes`.
    /// @param shapes           The call's shapes, by signature (see [Shapes#shapes()]).
    /// @param examplesPerShape The most examples kept a shape.
    /// @return The state.
    public static Map<String, Object> merge(final Map<String, Object> state,
                                            final Map<String, Object> result,
                                            final Map<String, Map<String, Object>> shapes,
                                            final int examplesPerShape) {
        return merge(state, result, shapes, examplesPerShape, at(Instant.now()));
    }

    /// Adds one survey_feed call to the record: counts add up, examples are kept up to the limit.
    ///
    /// @param state            The record's state so far, changed in place, or null for a new record.
    /// @param result           The call's result: `feed`, `format`, `time_range`, `streams_read`, `saturated`,
    ///                         `records_read` and `new_shapes`.
    /// @param shapes           The call's shapes, by signature (see [Shapes#shapes()]).
    /// @param examplesPerShape The most examples kept a shape.
    /// @param now              When, as [#at(Instant)] writes it.
    /// @return The state.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(final Map<String, Object> state,
                                            final Map<String, Object> result,
                                            final Map<String, Map<String, Object>> shapes,
                                            final int examplesPerShape,
                                            final String now) {
        final Map<String, Object> merged = state != null && !state.isEmpty()
                ? state
                : SampleProfiler.map("feed", result.get("feed"), "format", result.get("format"),
                        "streams_read", new ArrayList<>(), "surveys", new ArrayList<>(),
                        "shapes", new LinkedHashMap<String, Object>());
        merged.put("format", JsonValues.truthy(merged.get("format"))
                ? merged.get("format")
                : result.get("format"));
        merged.put("time_range", JsonValues.truthy(result.get("time_range"))
                ? result.get("time_range")
                : merged.get("time_range"));
        final List<Object> resultStreams = JsonValues.truthy(result.get("streams_read"))
                ? (List<Object>) result.get("streams_read")
                : List.of();
        merged.put("streams_read", sortedUnion((Collection<Object>) merged.get("streams_read"), resultStreams));
        merged.put("saturated", JsonValues.truthy(result.get("saturated")));
        ((List<Object>) merged.get("surveys")).add(SampleProfiler.map("at", now,
                "streams", new ArrayList<>(resultStreams),
                "records", result.getOrDefault("records_read", 0),
                "new_shapes", result.getOrDefault("new_shapes", 0)));
        final Map<String, Object> keptShapes = (Map<String, Object>) merged.get("shapes");
        for (final Map.Entry<String, Map<String, Object>> entry : shapes.entrySet()) {
            final Map<String, Object> shape = entry.getValue();
            final long count = ((Number) shape.get("count")).longValue();
            if (count == 0) {
                continue;
            }
            final Map<String, Object> kept = (Map<String, Object>) keptShapes.computeIfAbsent(entry.getKey(),
                    k -> SampleProfiler.map("count", 0L, "streams", new ArrayList<>(), "examples", new ArrayList<>(),
                            "first_seen", now));
            kept.put("count", ((Number) kept.get("count")).longValue() + count);
            final List<Object> streams = sortedUnion((Collection<Object>) kept.get("streams"),
                    (Collection<Object>) shape.get("streams"));
            kept.put("streams", new ArrayList<>(streams.subList(0, Math.min(50, streams.size()))));
            final List<Map<String, Object>> examples = (List<Map<String, Object>>) kept.get("examples");
            final Set<Object> texts = new LinkedHashSet<>();
            examples.forEach(example -> texts.add(example.get("text")));
            for (final Map<String, Object> example : (List<Map<String, Object>>) shape.get("examples")) {
                if (examples.size() >= examplesPerShape) {
                    break;
                }
                if (!texts.contains(example.get("text"))) {
                    examples.add(SampleProfiler.map("text", PyText.head((String) example.get("text"),
                            MAX_EXAMPLE_CHARS), "location", example.get("location")));
                }
            }
        }
        return merged;
    }

    // sorted(set(a) | set(b)), of stream ids.
    private static List<Object> sortedUnion(final Collection<Object> a, final Collection<Object> b) {
        final Set<Long> ids = new TreeSet<>();
        a.forEach(id -> ids.add(((Number) id).longValue()));
        b.forEach(id -> ids.add(((Number) id).longValue()));
        return new ArrayList<>(ids);
    }

    private static String cell(final String text, final int width) {
        final String flat = PyText.sub(WHITESPACE, " ", text).replace("|", "\\|");
        return PyText.len(flat) <= width
                ? flat
                : PyText.head(flat, width - 1) + "…";
    }

    /// Writes the survey document.
    ///
    /// @param state A survey state.
    /// @return The document's Markdown: the kinds of event, their examples, the surveys made, and the state.
    @SuppressWarnings("unchecked")
    public static String render(final Map<String, Object> state) {
        final List<Map.Entry<String, Object>> shapes = new ArrayList<>(shapes(state).entrySet());
        shapes.sort((a, b) -> Long.compare(count(b.getValue()), count(a.getValue())));
        final long total = Math.max(1, shapes.stream().mapToLong(entry -> count(entry.getValue())).sum());
        final Map<String, String> drop = dropped(state);
        final Map<String, Object> span = state.get("time_range") instanceof final Map<?, ?> range && !range.isEmpty()
                ? (Map<String, Object>) range
                : null;
        final List<Map<String, Object>> surveys = (List<Map<String, Object>>) state.get("surveys");
        final Map<String, Object> last = surveys.isEmpty()
                ? Map.of()
                : surveys.getLast();
        final long records = surveys.stream().mapToLong(s -> ((Number) s.get("records")).longValue()).sum();
        final String feed = PyText.str(state.get("feed"));
        final List<String> lines = new ArrayList<>(List.of("# " + docName(feed), "",
                "Kinds of event found in feed **" + feed + "** (" + PyText.str(state.get("format")) + "): "
                + shapes.size() + " from " + records + " records in " + ((List<?>) state.get("streams_read")).size()
                + " streams"
                + (span != null
                        ? ", created " + PyText.str(span.get("oldest")) + " to " + PyText.str(span.get("newest"))
                        : "") + ". "
                + (JsonValues.truthy(state.get("saturated"))
                        ? "The last survey found nothing new: the feed looks covered."
                        : "Not saturated yet: more streams may show more kinds of event.")
                + (drop.isEmpty()
                        ? ""
                        : " " + drop.size() + " kind(s) are left untranslated by choice (Handling)."), "",
                "Last survey: " + PyText.str(last.get("at")) + ". Examples are the feed's own records; where each one "
                + "is (stream:part:record) is given so it can be stepped. Streams expire under retention, so older "
                + "locations may be gone.", "",
                "## Kinds of event", "",
                "| # | Shape | Records | Share | Streams | Handling | Example |",
                "| --- | --- | --- | --- | --- | --- | --- |"));
        int n = 1;
        for (final Map.Entry<String, Object> entry : shapes) {
            final String signature = entry.getKey();
            final Map<String, Object> shape = (Map<String, Object>) entry.getValue();
            final List<Map<String, Object>> examples = (List<Map<String, Object>>) shape.get("examples");
            final String example = examples.isEmpty()
                    ? ""
                    : PyText.str(examples.getFirst().get("text"));
            final String handling = drop.containsKey(signature)
                    ? "left untranslated: " + cell(drop.get(signature), 60)
                    : "translate";
            lines.add("| " + n + " | " + cell(signature, 90) + " | " + count(shape) + " | "
                      + PyText.floatRepr(PyText.round(100.0 * count(shape) / total, 1)) + "% | "
                      + ((List<?>) shape.get("streams")).size() + " | " + handling + " | " + cell(example, 100) + " |");
            n++;
        }
        lines.addAll(List.of("", "## Examples", ""));
        n = 1;
        for (final Map.Entry<String, Object> entry : shapes) {
            lines.add("**" + n + ". " + entry.getKey() + "**");
            lines.add("");
            final Map<String, Object> shape = (Map<String, Object>) entry.getValue();
            for (final Map<String, Object> example : (List<Map<String, Object>>) shape.get("examples")) {
                final Map<String, Object> location = (Map<String, Object>) example.get("location");
                lines.addAll(List.of("Stream " + PyText.str(location.get("stream")) + ", part "
                                     + PyText.str(location.get("part")) + ", record "
                                     + PyText.str(location.get("record")) + ":", "", "```",
                        PyText.str(example.get("text")), "```", ""));
            }
            n++;
        }
        lines.addAll(List.of("## Surveys", "", "| When | Streams read | Records | New kinds |",
                "| --- | --- | --- | --- |"));
        for (final Map<String, Object> survey : surveys) {
            final List<String> streams = new ArrayList<>();
            ((List<?>) survey.get("streams")).forEach(stream -> streams.add(PyText.str(stream)));
            lines.add("| " + PyText.str(survey.get("at")) + " | " + String.join(", ", streams) + " | "
                      + PyText.str(survey.get("records")) + " | " + PyText.str(survey.get("new_shapes")) + " |");
        }
        lines.addAll(List.of("", "## Survey state", "",
                "Read by survey_feed to carry on where the last survey stopped; do not edit.", "",
                "```json survey-state", PyJson.dumps(state, 1), "```", ""));
        return String.join("\n", lines);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shapes(final Map<String, Object> state) {
        final Object shapes = state.get("shapes");
        return shapes instanceof Map<?, ?>
                ? (Map<String, Object>) shapes
                : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static long count(final Object shape) {
        return ((Number) ((Map<String, Object>) shape).get("count")).longValue();
    }
}
