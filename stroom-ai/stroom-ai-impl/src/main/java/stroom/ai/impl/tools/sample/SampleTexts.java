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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolRefusal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What counts as sample data (the Stroom MCP server's `utils/samples.py`).
///
/// A client hands the model file attachments as references; a model that does not read them passes the paths on,
/// and the server then profiles, uploads and plans around a Windows path. The server cannot read the client's
/// files, so a sample that is a path, a list of paths or an attachment reference is refused with the instruction a
/// model can act on: pass the file's text.
///
/// Small models behind a local tool-call parser also send the text broken up: a list holding a `{name: text}`
/// object, or (Gemma 4 on vLLM) the file's text cut at ':' and ',' into the keys and values of an object. A samples
/// argument therefore takes those shapes too (a map of file name to text, a list of texts or of `{name: text}`
/// objects, or one text), so the client's schema check doesn't reject them with a bare "must be string" the model
/// retries unchanged; [#asNamedSamples(Object, String)] then reads what it can and says plainly what arrived broken.
public final class SampleTexts {

    /// How much sample text a model should send: enough records to tell the format and fields, no more. The model
    /// writes it out a token at a time (seen: Gemma 4 31B at ~22 tokens a second took over 75 s for 4,400
    /// characters, and read 100 lines, 60 KB, it would have taken over ten minutes to send).
    public static final String SAMPLE_GUIDE = "about 10 records and at most 8,000 characters a file: you write this "
                                              + "text out a token at a time, so more only costs time. Files on the "
                                              + "user's disk are better given as files= (their paths): their text "
                                              + "then never passes through you";

    private static final int LONG = 20_000;
    private static final Pattern PATH = PyText.re(
            "^(?:[A-Za-z]:[\\\\/]|\\\\\\\\[^\\\\]+\\\\|/(?:[\\w.-]+/)+|~/|file://|#file:|#attachment:)[^\\n]*$");
    private static final Pattern REFERENCE = PyText.re("^#(?:file|attachment):\\S+$");
    // What a file reader leaves where it cut a file short: VS Code's read_file ends a long line with
    // '[... truncated at 2000 characters]'.
    private static final Pattern CUT = PyText.re("truncated at \\d[\\d,]* (?:characters|chars|bytes|lines)|"
                                                 + "\\[\\.\\.\\.\\s*(?:\\d[\\d,]* (?:more )?(?:characters|lines|bytes)"
                                                 + "|truncated)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_SEPARATORS = PyText.re("[,\\n;]+");
    private static final List<String> NAME_KEYS = List.of("name", "file", "filename", "file_name", "path");
    private static final List<String> TEXT_KEYS = List.of("text", "content", "contents", "data", "sample");

    private SampleTexts() {
        // Static methods only.
    }

    /// @param token A token of a sample.
    /// @return True if it is a file path or an attachment reference.
    public static boolean pathLike(final String token) {
        final String text = PyText.strip(PyText.strip(token), "\"'");
        return !text.isEmpty() && (PyText.match(PATH, text) || PyText.match(REFERENCE, text));
    }

    /// @param sample A sample's text, or null.
    /// @param name   What the sample is called in the message, e.g. `sample` or `samples[0]`.
    /// @return Why the text is not sample data (empty, a file path or list of paths, an attachment reference, or a
    /// file a reader cut short), or null when it is.
    public static String whyNotData(final String sample, final String name) {
        final String text = PyText.strip(sample == null
                ? ""
                : sample);
        if (text.isEmpty()) {
            return name + " is empty: pass the file's text";
        }
        final Matcher cut = CUT.matcher(text);
        if (cut.find()) {
            return name + " holds a file reader's cut (" + PyText.repr(cut.group()) + "): you didn't get the whole "
                   + "file, so what was passed isn't its data. Don't trim, complete or repair it (an agent completed a "
                   + "cut record with values it made up). Send the files with upload_sample files=[their paths] (a "
                   + "command per file for the user's terminal: it goes from their disk to Stroom whole, not through "
                   + "you); tools then take its stream_ids.";
        }
        final List<String> tokens = new ArrayList<>();
        for (final String token : TOKEN_SEPARATORS.split(text, -1)) {
            if (!PyText.isBlank(token)) {
                tokens.add(token);
            }
        }
        if (!tokens.isEmpty() && tokens.size() <= 50 && tokens.stream().allMatch(SampleTexts::pathLike)) {
            final String shown = PyText.head(PyText.strip(tokens.getFirst()), 80);
            return name + " is a file path or attachment reference (" + PyText.repr(shown) + "), not the file's "
                   + "content. This server cannot read the client's files: read the file and pass its text (every "
                   + "line of it) as the sample.";
        }
        return null;
    }

    /// @param sample A sample's text.
    /// @param name   What the sample is called in the message.
    /// @throws ToolRefusal if it isn't sample data, saying why (see [#whyNotData(String, String)]).
    public static void checkSample(final String sample, final String name) {
        final String reason = whyNotData(sample, name);
        if (reason != null) {
            throw new ToolRefusal(reason);
        }
    }

    // A key no file name has: more than one line (or an escaped newline), quotes, or the length of a record.
    private static boolean notAName(final String key) {
        return key.contains("\n") || key.contains("\\n") || key.contains("\"") || PyText.len(key) > 255;
    }

    /// @param named Samples by name.
    /// @return Why they are not file names and texts but one file's text cut into pieces by the tool call, or null.
    public static String whyBroken(final Map<String, ?> named) {
        final String odd = named.keySet().stream().filter(SampleTexts::notAName).findFirst().orElse(null);
        if (odd == null) {
            return null;
        }
        return "samples arrived broken: the tool call cut the file's text into the keys and values of an object (keys "
               + "such as " + PyText.repr(PyText.head(odd, 60)) + "), most likely at the ':' and ',' in it. The data "
               + "is fine; the arguments are not. Call again with samples as a list of strings, each one file's whole "
               + "text as a single JSON string (newlines escaped as \\n, quotes as \\\"), not an object.";
    }

    // A text sent with its newlines escaped twice (literal \n, no line breaks) has them restored.
    private static String unescape(final String text) {
        if (!PyText.strip(text).contains("\n") && PyText.count(text, "\\n") >= 2) {
            return text.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
        }
        return text;
    }

    // (name, text) from {name: ..., text: ...}, the shape models give one file; null for {file name: text}.
    private static OneFile recordOfOneFile(final Map<?, ?> item) {
        final Map<String, Object> keys = new LinkedHashMap<>();
        item.keySet().forEach(k -> keys.put(String.valueOf(k).toLowerCase(Locale.ROOT), k));
        final Object textKey = TEXT_KEYS.stream().filter(keys::containsKey).findFirst().map(keys::get).orElse(null);
        if (textKey == null) {
            return null;
        }
        for (final String key : keys.keySet()) {
            if (!NAME_KEYS.contains(key) && !TEXT_KEYS.contains(key)) {
                return null;
            }
        }
        final Object nameKey = NAME_KEYS.stream().filter(keys::containsKey).findFirst().map(keys::get).orElse(null);
        return new OneFile(nameKey != null
                ? PyText.str(item.get(nameKey))
                : "", item.get(textKey));
    }

    /// Samples as name to text, from a map by file name, a list of texts or `{name: text}` objects, or one text;
    /// each checked. An object that is one file's text cut into pieces is refused with what to send instead.
    ///
    /// @param samples The samples argument as the call gave it: a [Map], a [List], a [String] or null.
    /// @param single  A single sample given apart (named `sample` unless the samples have one so named), or null.
    /// @return The samples by name, their doubly escaped newlines restored.
    /// @throws ToolRefusal if they arrived broken, or one isn't sample data.
    public static Map<String, String> asNamedSamples(final Object samples, final String single) {
        final Map<String, Object> named = new LinkedHashMap<>();
        if (samples instanceof final Map<?, ?> map) {
            map.forEach((k, v) -> named.put(String.valueOf(k), v));
        } else if (samples instanceof final List<?> list) {
            int n = 1;
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map && recordOfOneFile(map) != null) {
                    final OneFile record = recordOfOneFile(map);
                    named.put(record.name().isEmpty()
                            ? "sample " + n
                            : record.name(), record.text());
                } else if (item instanceof final Map<?, ?> map) {
                    map.forEach((k, v) -> named.put(String.valueOf(k), v));
                } else {
                    named.put("sample " + n, item);
                }
                n++;
            }
        } else if (samples instanceof final String text) {
            named.put("sample", text);
        }
        if (single != null && !single.isEmpty()) {
            named.putIfAbsent("sample", single);
        }
        final String broken = whyBroken(named);
        if (broken != null) {
            throw new ToolRefusal(broken);
        }
        final Map<String, String> out = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : named.entrySet()) {
            if (!(entry.getValue() instanceof final String text)) {
                throw new ToolRefusal(entry.getKey() + ": a sample is the file's text, not "
                                      + typeName(entry.getValue()));
            }
            checkSample(text, entry.getKey());
            out.put(entry.getKey(), unescape(text));
        }
        return out;
    }

    // The type's name as Python says it.
    private static String typeName(final Object value) {
        return switch (value) {
            case null -> "NoneType";
            case final Boolean bool -> "bool";
            case final Double number -> "float";
            case final Number number -> "int";
            case final Map<?, ?> map -> "dict";
            case final List<?> list -> "list";
            default -> value.getClass().getSimpleName();
        };
    }

    /// @param named Samples by name.
    /// @return A note for next time when the texts sent were far more than profiling needs, or null.
    public static String longSamplesNote(final Map<String, String> named) {
        final List<String> said = new ArrayList<>();
        named.forEach((name, text) -> {
            if (PyText.len(text) > LONG) {
                said.add(name + " (" + PyText.thousands(PyText.len(text)) + " characters)");
            }
        });
        if (said.isEmpty()) {
            return null;
        }
        return "More sample text than profiling needs: " + String.join(", ", said) + ". About 10 records a file is "
               + "enough; for files on the user's disk give their paths (start_onboarding files=), so the text never "
               + "passes through you.";
    }


    // --------------------------------------------------------------------------------


    // One file's name ("" for none) and text, as a model gave them.
    private record OneFile(String name, Object text) {

    }
}
