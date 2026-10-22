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

import stroom.util.shared.NullSafe;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What counts as sample data in a tool's arguments (the Stroom MCP server's `utils/samples.py`).
///
/// A client hands the model file attachments as references; a model that does not read them passes the paths on,
/// and the tools would then profile, upload and plan around a Windows path. Stroom cannot read the client's files,
/// so a sample that is a path, a list of paths or an attachment reference is refused with the instruction a model
/// can act on: pass the file's text.
///
/// Small models behind a local tool-call parser also send the text broken up: a list holding a {name: text} object,
/// or (Gemma 4 on vLLM) the file's text cut at ':' and ',' into the keys and values of an object. The tools read
/// what they can and say plainly what arrived broken.
final class SampleArguments {

    /// How much sample text a model should send: enough records to tell the format and fields, no more. The model
    /// writes it out a token at a time (seen: Gemma 4 31B at ~22 tokens a second took over 75 s for 4,400
    /// characters, and read 100 lines, 60 KB, it would have taken over ten minutes to send).
    static final String SAMPLE_GUIDE = "about 10 records and at most 8,000 characters a file: you write this text out "
                                       + "a token at a time, so more only costs time. Files on the user's disk are "
                                       + "better given as files= (their paths): their text then never passes "
                                       + "through you";

    private static final Pattern PATH = Pattern.compile("^(?:[A-Za-z]:[\\\\/]|\\\\\\\\[^\\\\]+\\\\|/(?:[\\w.-]+/)+"
                                                        + "|~/|file://|#file:|#attachment:)[^\\n]*$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern REFERENCE = Pattern.compile("^#(?:file|attachment):\\S+$",
            Pattern.UNICODE_CHARACTER_CLASS);
    // What a file reader leaves where it cut a file short: VS Code's read_file ends a long line with
    // '[... truncated at 2000 characters]'.
    private static final Pattern CUT = Pattern.compile("truncated at \\d[\\d,]* (?:characters|chars|bytes|lines)"
                                                       + "|\\[\\.\\.\\.\\s*(?:\\d[\\d,]* (?:more )?(?:characters"
                                                       + "|lines|bytes)|truncated)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SEPARATORS = Pattern.compile("[,\\n;]+");
    private static final List<String> NAME_KEYS = List.of("name", "file", "filename", "file_name", "path");
    private static final List<String> TEXT_KEYS = List.of("text", "content", "contents", "data", "sample");
    private static final int LONG = 20_000;

    private SampleArguments() {
        // Static methods only.
    }

    /// @param token Some text.
    /// @return Whether it is a file path or an attachment reference.
    static boolean pathLike(final String token) {
        final String value = stripQuotes(NullSafe.string(token).strip());
        return !value.isEmpty() && (PATH.matcher(value).matches() || REFERENCE.matcher(value).matches());
    }

    /// @param sample A sample, as given.
    /// @param name   Its name, to say which.
    /// @return Why it is not sample data (empty, a file path or list of paths, an attachment reference, or a file a
    /// reader cut short), or empty if it is.
    static Optional<String> whyNotData(final String sample, final String name) {
        final String text = NullSafe.string(sample).strip();
        if (text.isEmpty()) {
            return Optional.of(name + " is empty: pass the file's text");
        }
        final Matcher cut = CUT.matcher(text);
        if (cut.find()) {
            return Optional.of(name + " holds a file reader's cut (" + pythonRepr(cut.group()) + "): you didn't get "
                               + "the whole file, so what was passed isn't its data. Don't trim, complete or repair "
                               + "it (an agent completed a cut record with values it made up). Send the files with "
                               + "upload_sample files=[their paths] (a command per file for the user's terminal: it "
                               + "goes from their disk to Stroom whole, not through you); tools then take its "
                               + "stream_ids.");
        }
        final List<String> tokens = Arrays.stream(SEPARATORS.split(text))
                .filter(token -> !token.isBlank())
                .toList();
        if (!tokens.isEmpty() && tokens.size() <= 50 && tokens.stream().allMatch(SampleArguments::pathLike)) {
            final String shown = tokens.getFirst().strip();
            return Optional.of(name + " is a file path or attachment reference ("
                               + pythonRepr(shown.substring(0, Math.min(80, shown.length()))) + "), not the file's "
                               + "content. This server cannot read the client's files: read the file and pass its "
                               + "text (every line of it) as the sample.");
        }
        return Optional.empty();
    }

    /// Samples as {name: text}, from a map by file name, a list of texts or {name: text} objects, or one text; each
    /// checked. An object that is one file's text cut into pieces is refused with what to send instead.
    ///
    /// @param samples The samples argument, as the call gave it, or null.
    /// @return The samples by name; none for null.
    /// @throws ToolRefusal if they are not sample data.
    static Map<String, String> namedSamples(final Object samples) {
        return namedSamples(samples, null);
    }

    /// As [#namedSamples(Object)], with one sample given on its own as well (profile_sample's `sample`), named
    /// `sample` unless the samples already have one of that name.
    ///
    /// @param samples The samples argument, as the call gave it, or null.
    /// @param single  One sample's text, or null.
    /// @return The samples by name; none for null.
    /// @throws ToolRefusal if they are not sample data.
    static Map<String, String> namedSamples(final Object samples, final String single) {
        final Map<String, Object> named = new LinkedHashMap<>();
        if (samples instanceof final Map<?, ?> map) {
            map.forEach((key, value) -> named.put(String.valueOf(key), value));
        } else if (samples instanceof final List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                final Object item = list.get(i);
                final Optional<Entry<String, Object>> one = item instanceof final Map<?, ?> map
                        ? recordOfOneFile(map)
                        : Optional.empty();
                if (one.isPresent()) {
                    named.put(NullSafe.nonBlankStringElse(one.get().getKey(), "sample " + (i + 1)),
                            one.get().getValue());
                } else if (item instanceof final Map<?, ?> map) {
                    map.forEach((key, value) -> named.put(String.valueOf(key), value));
                } else {
                    named.put("sample " + (i + 1), item);
                }
            }
        } else if (samples instanceof final String text) {
            named.put("sample", text);
        }
        if (NullSafe.isNonEmptyString(single)) {
            named.putIfAbsent("sample", single);
        }
        final Optional<String> broken = whyBroken(named);
        if (broken.isPresent()) {
            throw new ToolRefusal(broken.get());
        }
        final Map<String, String> checked = new LinkedHashMap<>();
        for (final Entry<String, Object> entry : named.entrySet()) {
            if (!(entry.getValue() instanceof final String text)) {
                throw new ToolRefusal(entry.getKey() + ": a sample is the file's text, not "
                                      + pythonType(entry.getValue()));
            }
            final Optional<String> why = whyNotData(text, entry.getKey());
            if (why.isPresent()) {
                throw new ToolRefusal(why.get());
            }
            checked.put(entry.getKey(), unescape(text));
        }
        return checked;
    }

    /// @param named Samples by name.
    /// @return A note for next time when the texts sent were far more than profiling needs, or empty.
    static Optional<String> longSamplesNote(final Map<String, String> named) {
        final Map<String, Integer> lengths = new LinkedHashMap<>();
        named.forEach((name, text) -> {
            if (text.length() > LONG) {
                lengths.put(name, text.length());
            }
        });
        if (lengths.isEmpty()) {
            return Optional.empty();
        }
        final String said = lengths.entrySet().stream()
                .map(entry -> entry.getKey() + " (" + String.format(Locale.ROOT, "%,d", entry.getValue())
                              + " characters)")
                .collect(Collectors.joining(", "));
        return Optional.of("More sample text than profiling needs: " + said + ". About 10 records a file is enough; "
                           + "for files on the user's disk give their paths (start_onboarding files=), so the text "
                           + "never passes through you.");
    }

    /// @param values Strings.
    /// @return Them as Python writes a list of strings: `['a', 'b']`.
    static String pythonList(final List<String> values) {
        return values.stream()
                .map(SampleArguments::pythonRepr)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /// @param text A string.
    /// @return It as Python's repr writes it: in single quotes (double, if it holds a single quote and no double),
    /// backslashes doubled.
    static String pythonRepr(final String text) {
        final String value = NullSafe.string(text);
        final char quote = value.contains("'") && !value.contains("\"")
                ? '"'
                : '\'';
        final StringBuilder sb = new StringBuilder().append(quote);
        for (final char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c == quote) {
                        sb.append('\\');
                    }
                    sb.append(c);
                }
            }
        }
        return sb.append(quote).toString();
    }

    // Why {name: text} is not file names and texts but one file's text cut into pieces by the tool call.
    private static Optional<String> whyBroken(final Map<String, Object> named) {
        final Optional<String> odd = named.keySet().stream()
                .filter(SampleArguments::notAName)
                .findFirst();
        return odd.map(key -> "samples arrived broken: the tool call cut the file's text into the keys and values of "
                              + "an object (keys such as " + pythonRepr(key.substring(0, Math.min(60, key.length())))
                              + "), most likely at the ':' and ',' in it. The data is fine; the arguments are not. "
                              + "Call again with samples as a list of strings, each one file's whole text as a "
                              + "single JSON string (newlines escaped as \\n, quotes as \\\"), not an object.");
    }

    // A key no file name has: more than one line (or an escaped newline), quotes, or the length of a record.
    private static boolean notAName(final String key) {
        return key.contains("\n") || key.contains("\\n") || key.contains("\"") || key.length() > 255;
    }

    // A text sent with its newlines escaped twice (literal \n, no line breaks) has them restored.
    private static String unescape(final String text) {
        if (!text.strip().contains("\n") && countOf(text, "\\n") >= 2) {
            return text.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
        }
        return text;
    }

    // (name, text) from {name: ..., text: ...}, the shape models give one file; empty for {file name: text}.
    private static Optional<Entry<String, Object>> recordOfOneFile(final Map<?, ?> item) {
        final Map<String, Object> keys = new LinkedHashMap<>();
        item.keySet().forEach(key -> keys.put(String.valueOf(key).toLowerCase(Locale.ROOT), key));
        final Optional<String> textKey = TEXT_KEYS.stream().filter(keys::containsKey).findFirst();
        final Set<String> others = new HashSet<>(keys.keySet());
        NAME_KEYS.forEach(others::remove);
        TEXT_KEYS.forEach(others::remove);
        if (textKey.isEmpty() || !others.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> nameKey = NAME_KEYS.stream().filter(keys::containsKey).findFirst();
        final String name = nameKey.map(key -> String.valueOf(item.get(keys.get(key)))).orElse("");
        return Optional.of(Map.entry(name, Objects.requireNonNullElse(item.get(keys.get(textKey.get())), "")));
    }

    private static int countOf(final String text, final String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    private static String stripQuotes(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && (text.charAt(start) == '"' || text.charAt(start) == '\'')) {
            start++;
        }
        while (end > start && (text.charAt(end - 1) == '"' || text.charAt(end - 1) == '\'')) {
            end--;
        }
        return text.substring(start, end);
    }

    // The name Python gives a value's type, as the Stroom MCP server said it.
    private static String pythonType(final Object value) {
        if (value == null) {
            return "NoneType";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "int";
        }
        if (value instanceof Number) {
            return "float";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        if (value instanceof Map<?, ?>) {
            return "dict";
        }
        return value.getClass().getSimpleName();
    }
}
