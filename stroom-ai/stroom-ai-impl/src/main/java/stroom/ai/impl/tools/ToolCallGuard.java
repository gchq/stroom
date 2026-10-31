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

import stroom.ai.impl.tools.ToolSchema.EnumSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What every call of one of Stroom's tools goes through, in the chat and over MCP alike: the arguments are read
/// leniently, and calls that keep failing are stopped.
///
/// Lenient arguments. Unknown arguments are dropped, not fatal: a small model that invents a flag
/// ("save_text_converter": "true") would otherwise be stuck, so the tool is called without it and the result says
/// which were ignored. A choice given with stray punctuation or in another case (",XML_FRAGMENT", "xml_fragment") is
/// read as the choice it plainly is, and the result says so: seen, an agent sending ",XML_FRAGMENT" again and again,
/// refused each time. Text mangled by a Windows code page round trip ('â€”' for '—') is put back in every string
/// argument, and the result says so (see [MojibakeRepair]): seen in VS Code, an agent stalled a terminal trying to
/// fix a doc body itself.
///
/// Repeated failures. Seen in a test environment: an agent called build_translation_xslt over a hundred times with
/// near-identical mappings, each a minute and more, adding backslashes to a regex that failed for another reason.
/// Per session, a tool's failures in a row are counted: from the fifth (and every fifth after) the reply says to
/// step back, with what to do instead for that tool. The same call (tool and arguments) failing three times is
/// answered from that failure after, without running it again, for a tool whose answer to the same arguments
/// can't change. A success resets both.
@Singleton
public class ToolCallGuard {

    /// Identical failing calls run; after that, answered from the last failure.
    static final int SAME_CALL_LIMIT = 3;
    /// Failed calls of one tool in a row before the reply says to step back (and every fifth after).
    static final int STREAK = 5;
    /// How long a session's failures are remembered.
    static final Duration TTL = Duration.ofMinutes(30);
    /// The most characters of a failure kept to repeat.
    static final int MAX_MESSAGE = 300;

    // Answered from the last failure only where the same arguments give the same answer: the mapping and drafting
    // tools. Another tool's call may succeed once the user has changed something in Stroom.
    private static final Set<String> SAME_ANSWER = Set.of("build_translation_xslt", "draft_translation_mapping",
            "build_data_splitter", "profile_sample", "check_xslt", "check_events", "validate_events",
            "create_text_converter", "save_text_converter");
    // Failures that may pass on their own, and are retried as they are: never counted.
    private static final Pattern TRANSIENT = Pattern.compile(
            "call again|timed? ?out|temporar|unavailable|could not connect|connection|\\b50[234]\\b|try again",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> ADVICE = Map.of(
            "build_translation_xslt",
            "Start again from a clean mapping instead of patching this one: draft_translation_mapping with the "
            + "sample streams gives one. Change one thing at a time and read each problem in full: an extraction "
            + "regex that doesn't match says where it stops matching and what the text has there. A regex is "
            + "written once, as XPath reads it: only your call's JSON doubles its backslashes, nothing else escapes "
            + "it again. If the data is not what you expected, show the user a record and ask.",
            "create_pipeline",
            "find_pipeline_templates for the sample's parser (profile_sample names it), then create_pipeline from "
            + "that template; for XML fragments, replace_parser='XMLFragmentParser' with the wrapper converter from "
            + "profile_sample. If no template fits, ask the user which to use.",
            "step_sample",
            "Read the groups' messages in full and fix their cause in the mapping (build_translation_xslt "
            + "uuid=...), not the same thing again. If a cause is not yours to fix (the data, a template element), "
            + "ask the user.");
    private static final String DEFAULT_ADVICE = "Step back: read the last error in full and change the approach, "
                                                 + "not the same arguments again. If it is unclear what is wrong, "
                                                 + "show the user what you have and ask.";
    // Stray characters round a choice that is plainly meant.
    private static final String STRAY = " \t\r\n,;:.'\"`()[]{}<>";

    private final Map<String, SessionFailures> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    /// A guard on the system clock.
    public ToolCallGuard() {
        this(System::currentTimeMillis);
    }

    /// @param clock The time now, in milliseconds.
    ToolCallGuard(final LongSupplier clock) {
        this.clock = clock;
    }

    /// Makes a call through the guard.
    ///
    /// @param tool    The tool.
    /// @param call    The call, as the model made it.
    /// @param context The call's context; its session is whose failures are counted.
    /// @param runner  Runs the tool with the call as read.
    /// @return What the tool returned, with notes on how its arguments were read and on repeated failures; or the
    /// last failure of the same call, without running it again.
    public ToolResult run(final StroomTool tool,
                          final ToolCall call,
                          final ToolCallContext context,
                          final BiFunction<ToolCall, ToolCallContext, ToolResult> runner) {
        final String name = call.name();
        final Read read = read(tool, call);
        final String digest = digest(read.call().arguments());
        final SessionFailures failures = failures(context);
        final String advice = ADVICE.getOrDefault(name, DEFAULT_ADVICE);
        synchronized (failures) {
            final SameCall same = failures.calls.get(new CallKey(name, digest));
            if (same != null && same.failures >= SAME_CALL_LIMIT && SAME_ANSWER.contains(name)) {
                same.failures++;
                return withNotes(ToolResult.error("Not run again: this exact call has already failed "
                                                  + (same.failures - 1) + " times, last with: " + same.message
                                                  + ". Running it again changes nothing. " + advice),
                        read.notes());
            }
        }
        final ToolResult result = Objects.requireNonNullElseGet(runner.apply(read.call(), context),
                () -> ToolResult.text(""));
        final String failure = failure(result);
        final ToolResult noted;
        if (failure == null) {
            synchronized (failures) {
                failures.streaks.remove(name);
                failures.calls.remove(new CallKey(name, digest));
            }
            noted = result;
        } else {
            final String note = failed(failures, name, digest, failure, advice);
            noted = note == null
                    ? result
                    : withRepeatNote(result, note);
        }
        return withNotes(noted, read.notes());
    }

    /// Reads a call's arguments leniently.
    ///
    /// @param tool The tool called.
    /// @param call The call, as the model made it.
    /// @return The call with its arguments read leniently, and what was changed.
    static Read read(final StroomTool tool, final ToolCall call) {
        final Map<String, Object> arguments = arguments(call.arguments());
        if (arguments == null) {
            return new Read(call, List.of());
        }
        final Map<String, ToolSchema> properties = NullSafe.getOrElse(tool.definition().parameters(),
                ObjectSchema::properties, Map.of());
        final List<String> readAs = new ArrayList<>();
        properties.forEach((key, property) -> {
            if (property instanceof final EnumSchema choice) {
                final String fixed = plainChoice(arguments.get(key), choice.values());
                if (fixed != null) {
                    readAs.add(key + " '" + arguments.get(key) + "' as '" + fixed + "'");
                    arguments.put(key, fixed);
                }
            }
        });
        final List<String> ignored = properties.isEmpty()
                ? List.of()
                : arguments.keySet().stream().filter(key -> !properties.containsKey(key)).sorted().toList();
        ignored.forEach(arguments::remove);
        int mended = 0;
        for (final Entry<String, Object> entry : arguments.entrySet()) {
            final Mended value = repaired(entry.getValue());
            entry.setValue(value.value());
            mended += value.count();
        }
        final List<String> notes = new ArrayList<>();
        if (!ignored.isEmpty()) {
            notes.add("Ignored unknown argument(s) " + ignored + ": this tool takes "
                      + new TreeSet<>(properties.keySet()) + ".");
        }
        if (!readAs.isEmpty()) {
            notes.add("Read " + String.join(", ", readAs) + ": give the choice exactly as listed.");
        }
        if (mended > 0) {
            notes.add(MojibakeRepair.note(mended));
        }
        return notes.isEmpty()
                ? new Read(call, List.of())
                : new Read(new ToolCall(call.id(), call.name(), JsonUtil.writeValueAsString(arguments, false)),
                        List.copyOf(notes));
    }

    /// @param value   A value given for a choice.
    /// @param choices The choices.
    /// @return The choice the value plainly means, when it isn't one as given: stray punctuation round it, or
    /// another case. Null if it is one as given, or plainly none.
    static String plainChoice(final Object value, final List<String> choices) {
        if (!(value instanceof final String text) || NullSafe.isEmptyCollection(choices) || choices.contains(text)) {
            return null;
        }
        final String bare = ToolText.strip(text, STRAY);
        return choices.stream()
                .filter(bare::equals)
                .findFirst()
                .orElseGet(() -> choices.stream()
                        .filter(choice -> choice.toLowerCase(Locale.ROOT).equals(bare.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElse(null));
    }

    // Counts a failure; the note to add to it, if it is a repeat or one of a run.
    private String failed(final SessionFailures failures,
                          final String name,
                          final String digest,
                          final String message,
                          final String advice) {
        if (TRANSIENT.matcher(message).find()) {
            return null;
        }
        synchronized (failures) {
            final SameCall same = failures.calls.computeIfAbsent(new CallKey(name, digest),
                    key -> new SameCall());
            same.failures++;
            same.message = message;
            final int streak = failures.streaks.merge(name, 1, Integer::sum);
            if (streak >= STREAK && (streak - STREAK) % STREAK == 0) {
                return "That is " + streak + " failed " + name + " calls in a row. " + advice;
            }
            if (same.failures > 1) {
                return "The same arguments as a call that already failed (" + same.failures + " times): change "
                       + "them. " + advice;
            }
            return null;
        }
    }

    /// @param result What a tool returned.
    /// @return What a failure says: an error result's first line, or a result with ok false, or a mapping the tool
    /// couldn't read. Null if the call didn't fail.
    static String failure(final ToolResult result) {
        if (result.isError()) {
            final String text = result.text().strip();
            final String firstLine = text.isEmpty()
                    ? "error"
                    : text.lines().findFirst().orElse(text);
            return shorten(firstLine);
        }
        final Map<String, Object> body = jsonObject(result.text());
        if (body != null && (Boolean.FALSE.equals(body.get("ok")) || "needs_mapping".equals(body.get("status")))) {
            final String problems = body.get("problems") instanceof final List<?> list
                    ? list.stream().limit(2).map(String::valueOf).collect(Collectors.joining("; "))
                    : "";
            return shorten(problems.isEmpty()
                    ? "not ok"
                    : problems);
        }
        return null;
    }

    // A failed call's note: inside the result when it is one JSON object (as repeated), else after its text.
    private static ToolResult withRepeatNote(final ToolResult result, final String note) {
        final Map<String, Object> body = result.isError()
                ? null
                : jsonObject(result.text());
        if (body != null) {
            body.put("repeated", note);
            return withText(result, JsonUtil.writeValueAsString(body, false));
        }
        return withText(result, result.text() + "\n\n" + note);
    }

    // The notes on how the arguments were read: inside the result when it is one JSON object (as argument_notes,
    // first), else as text after it. A note after the JSON made the reply unreadable as JSON to a client that joins
    // its texts (seen in Stroom's assistant: a survey result saved to a file, then jq refused it).
    private static ToolResult withNotes(final ToolResult result, final List<String> notes) {
        if (notes.isEmpty()) {
            return result;
        }
        final Map<String, Object> body = jsonObject(result.text());
        if (body != null) {
            final Map<String, Object> noted = new LinkedHashMap<>();
            noted.put("argument_notes", notes);
            noted.putAll(body);
            return withText(result, JsonUtil.writeValueAsString(noted, false));
        }
        return withText(result, result.text() + " " + String.join(" ", notes));
    }

    // The result with other text, and all else as it was.
    private static ToolResult withText(final ToolResult result, final String text) {
        final ToolResult base = result.isError()
                ? ToolResult.error(text)
                : ToolResult.text(text);
        ToolResult copy = base.withStructuredContent(result.structuredContent()).withResources(result.resources());
        for (final ToolUserAction action : result.userActions()) {
            copy = copy.withUserAction(action);
        }
        return copy;
    }

    private SessionFailures failures(final ToolCallContext context) {
        final long now = clock.getAsLong();
        sessions.values().removeIf(state -> now - state.usedMs > TTL.toMillis());
        final ToolSession session = context.session();
        final String key = session.kind() + ":" + NullSafe.nonBlankStringElse(session.id(), "none");
        final SessionFailures failures = sessions.computeIfAbsent(key, ignored -> new SessionFailures());
        failures.usedMs = now;
        return failures;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(final String json) {
        if (NullSafe.isBlankString(json)) {
            return null;
        }
        try {
            final Object parsed = JsonUtil.readValue(json, Object.class);
            return parsed instanceof final Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map)
                    : null;
        } catch (final RuntimeException e) {
            // Not JSON: the tool says what is wrong with it.
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonObject(final String text) {
        final String stripped = NullSafe.string(text).strip();
        if (!stripped.startsWith("{")) {
            return null;
        }
        try {
            final Object parsed = JsonUtil.readValue(stripped, Object.class);
            return parsed instanceof final Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map)
                    : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Mended repaired(final Object value) {
        return switch (value) {
            case final String text -> {
                final MojibakeRepair.Repaired repaired = MojibakeRepair.repair(text);
                yield new Mended(repaired.text(), repaired.count());
            }
            case final List<?> list -> {
                final List<Object> out = new ArrayList<>();
                int count = 0;
                for (final Object item : list) {
                    final Mended mended = repaired(item);
                    out.add(mended.value());
                    count += mended.count();
                }
                yield new Mended(out, count);
            }
            case final Map<?, ?> map -> {
                final Map<String, Object> out = new LinkedHashMap<>();
                int count = 0;
                for (final Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                    final Mended mended = repaired(entry.getValue());
                    out.put(entry.getKey(), mended.value());
                    count += mended.count();
                }
                yield new Mended(out, count);
            }
            case null, default -> new Mended(value, 0);
        };
    }

    private static String digest(final String arguments) {
        final Map<String, Object> parsed = arguments(arguments);
        // Keys sorted, so the same arguments in another order are the same call.
        final String canonical = parsed == null
                ? NullSafe.string(arguments)
                : JsonUtil.writeValueAsString(sorted(parsed), false);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object sorted(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> out = new TreeMap<>();
            ((Map<String, Object>) map).forEach((key, item) -> out.put(key, sorted(item)));
            return out;
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(ToolCallGuard::sorted).toList();
        }
        return value;
    }

    private static String shorten(final String text) {
        return text.length() > MAX_MESSAGE
                ? text.substring(0, MAX_MESSAGE)
                : text;
    }


    // --------------------------------------------------------------------------------


    /// A call as read: the arguments changed as needed, and notes saying how.
    ///
    /// @param call  The call to make.
    /// @param notes What was changed, for the result; empty if nothing was.
    record Read(ToolCall call, List<String> notes) {

    }

    private record CallKey(String toolName, String digest) {

    }

    private record Mended(Object value, int count) {

    }

    // One call's failures in a row.
    private static final class SameCall {

        private int failures;
        private String message;
    }

    // A session's failures.
    private static final class SessionFailures {

        private final Map<CallKey, SameCall> calls = new LinkedHashMap<>();
        private final Map<String, Integer> streaks = new LinkedHashMap<>();
        private volatile long usedMs;
    }
}
