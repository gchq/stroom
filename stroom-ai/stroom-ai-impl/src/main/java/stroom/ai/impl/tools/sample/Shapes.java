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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Groups records into shapes, one per kind of event, across the streams of a survey; `known` signatures (from an
/// earlier survey) are recognised. The Stroom MCP server's `survey.Shapes`.
///
/// A feed's streams rarely show every event type in one stream, so the agent samples stream after stream and
/// groups records into shapes until a few more streams add nothing new. A shape is signed by:
///
/// - JSON, XML, key=value: which fields a record has, plus the values of fields that usually name the event
///   (action, event, type, category, logger and similar). A message-like field (body, message, msg...) is
///   unwrapped: JSON inside it (after any text prefix or byte order mark) is signed the same way, and key=value
///   pairs in it keep the values of naming keys (type="LOGIN", or Action: [Login]). Its text is masked as below only
///   when nothing else names the event, so a wrapper (e.g. syslog shipped as JSON) doesn't hide the kinds. A field
///   of free text by another name (a Windows event's Data) is read for naming pairs too, never masked.
/// - Delimited: the values of those naming columns, or of the low-variety columns when none is named that way.
/// - CEF: the header's vendor, product, signature id and name.
/// - Syslog and other text: the message with its variable parts masked (numbers, addresses, quoted strings),
///   merged with other messages that differ only in a few words (user names, hosts), in the style of Drain.
///
/// A shape is JSON, as the Python server held it: `signature`, `known`, `count`, `examples` (each `text` and
/// `location`: `stream`, `part`, `record`) and `streams`.
public final class Shapes {

    /// A field or column that usually names the kind of event.
    public static final Pattern NAMING = PyText.re(
            "(^|[._-])(type|event|event_?type|event_?name|event_?id|action|category|activity|operation|op|kind|"
            + "result_?type|msg_?id|message_?id|log_?type|subtype|logger|logger_?name)$", Pattern.CASE_INSENSITIVE);
    /// A field holding the message itself, whose content may name the event.
    public static final Pattern MESSAGE = PyText.re("(^|[._-])(body|message|msg|log|text|payload)$",
            Pattern.CASE_INSENSITIVE);
    /// A merged word of a text template.
    public static final String WILD = "<*>";

    static final Pattern TOKEN_WITH_DIGIT = PyText.re("\\S*\\d\\S*");

    private static final Pattern KV = PyText.re("(?<![\\w.])([A-Za-z_][\\w.]*)=\\\\?\"?([\\w.:-]+)");
    // Key: [Value], as audit messages name the event (Secret Server: "Event: [User] Action: [Login Failed]").
    private static final Pattern BRACKETED = PyText.re(
            "(?<![\\w.])([A-Za-z_][\\w.]*)\\s*[:=]\\s*\\[([^\\[\\]]{1,60})\\]");
    private static final int DEPTH = 3;
    private static final List<Map.Entry<Pattern, String>> MASKS = List.of(
            Map.entry(PyText.re("\"[^\"]*\""), "<s>"),
            Map.entry(PyText.re("'[^']*'"), "<s>"),
            Map.entry(PyText.re("\\b[\\w.+-]+@[\\w-]+\\.[\\w.]+\\b"), "<email>"),
            Map.entry(PyText.re("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b"), "<ip>"),
            Map.entry(PyText.re("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"),
                    "<uuid>"),
            Map.entry(PyText.re("\\b(?:0x)?[0-9a-fA-F]{12,}\\b"), "<hex>"));
    private static final Pattern SYSLOG_5424_PARTS = PyText.re(
            "^<\\d+>1 \\S+ \\S+ (\\S+) \\S+ \\S+ (?:\\[.*?\\]|-) ?(.*)$");
    private static final Pattern SYSLOG_3164_PARTS = PyText.re(
            "^(?:<\\d+>)?\\w{3} [ \\d]\\d \\d{2}:\\d{2}:\\d{2} \\S+ ([^:\\[\\s]+)(?:\\[\\d+\\])?: ?(.*)$");
    private static final List<String> CEF_FIELDS = List.of("version", "vendor", "product", "device_version",
            "signature_id", "name", "severity");

    private final Map<String, Map<String, Object>> shapes = new LinkedHashMap<>();
    private final int examples;
    // Text templates by program and word count.
    private final Map<List<Object>, List<List<String>>> templates = new LinkedHashMap<>();

    /// Shapes with none known, keeping two examples each.
    public Shapes() {
        this(List.of(), 2);
    }

    /// @param known    Signatures from an earlier survey, recognised as known.
    /// @param examples The most examples kept a shape.
    public Shapes(final List<String> known, final int examples) {
        this.examples = examples;
        for (final String signature : known == null
                ? List.<String>of()
                : known) {
            addShape(signature, true);
            if (signature.startsWith("text:")) {
                final String rest = signature.substring(5);
                final int bar = rest.indexOf('|');
                final String program = bar < 0
                        ? rest
                        : rest.substring(0, bar);
                final String template = bar < 0
                        ? ""
                        : rest.substring(bar + 1);
                final List<String> tokens = new ArrayList<>(PyText.split(template, " "));
                templates.computeIfAbsent(List.of(program, tokens.size()), k -> new ArrayList<>()).add(tokens);
            }
        }
    }

    /// @return The shapes by signature, in the order first seen: each `signature`, `known`, `count`, `examples`
    /// and `streams`.
    public Map<String, Map<String, Object>> shapes() {
        return shapes;
    }

    private Map<String, Object> addShape(final String signature, final boolean known) {
        return shapes.computeIfAbsent(signature, k -> SampleProfiler.map("signature", signature, "known", known,
                "count", 0, "examples", new ArrayList<Map<String, Object>>(), "streams", new ArrayList<Long>()));
    }

    /// @param chunk A part's records.
    /// @param index A record's index.
    /// @return The record's signature: `row:...` for delimited records, `fields:...` for structured ones, `cef:...`
    /// for CEF, `text:<program>|<template>` for text.
    public String signature(final RecordSplitter.Chunk chunk, final int index) {
        final Map<String, Object> parsed = chunk.parsedAt(index);
        if (RecordSplitter.DELIMITED.equals(chunk.format())
            || (!chunk.namingColumns().isEmpty() && parsed != null)) {
            final List<String> columns = chunk.namingColumns();
            if (columns.isEmpty()) {
                return "row:all";
            }
            final List<String> values = new ArrayList<>();
            columns.forEach(c -> values.add(c + "=" + PyText.str(parsed == null
                    ? ""
                    : parsed.getOrDefault(c, ""))));
            return "row:" + String.join(", ", values);
        }
        if (parsed != null) {
            return "fields:" + fields(parsed, 0);
        }
        // A record of several lines is signed by its first: the entry, not its stack trace.
        final String record = chunk.records().get(index).split("\n", 2)[0];
        if (SampleProfiler.CEF_FORMAT.equals(chunk.format())) {
            final Map<String, String> header = cefHeader(record);
            if (header != null) {
                // The header names the kind of event: masked as text, 200 lines of 5 kinds came out as 3 shapes, one
                // per count of extension keys, the signature ids masked as numbers.
                return "cef:" + String.join("|", header.get("vendor"), header.get("product"),
                        header.get("signature_id"), header.get("name"));
            }
        }
        final String[] parts = syslogParts(record);
        return text(parts[0], parts[1]);
    }

    private static String lastPart(final String key) {
        final int at = key.lastIndexOf('@');
        return at < 0
                ? key
                : key.substring(at + 1);
    }

    private String fields(final Map<String, Object> parsed, final int depth) {
        final List<String> keys = new ArrayList<>(parsed.keySet());
        keys.sort(PyText::compare);
        final List<String> naming = new ArrayList<>();
        for (final String key : keys) {
            final Object value = parsed.get(key);
            if (PyText.search(NAMING, lastPart(key))
                && (value instanceof String || value instanceof Long || value instanceof Integer
                    || value instanceof BigInteger || value instanceof Boolean)
                && PyText.len(PyText.str(value)) <= 60) {
                naming.add(key + "=" + PyText.str(value));
            }
        }
        naming.sort(PyText::compare);
        String signature = String.join(",", keys) + (naming.isEmpty()
                ? ""
                : " | " + String.join(", ", naming));
        String message = null;
        for (final String key : keys) {
            if (PyText.search(MESSAGE, lastPart(key)) && parsed.get(key) instanceof final String text
                && !PyText.isBlank(text)) {
                message = key;
                break;
            }
        }
        boolean drain = naming.isEmpty();
        if (message == null) {
            // Free text under another name: seen in production, Secret Server's audit detail in a Windows event's
            // Data, every record alike but for the kind of event inside it. Read for naming pairs only.
            for (final String key : keys) {
                if (freeText(parsed.get(key))
                    && (message == null || PyText.len((String) parsed.get(key))
                                           > PyText.len((String) parsed.get(message)))) {
                    message = key;
                }
            }
            drain = false;
        }
        if (message != null && depth < DEPTH) {
            final String inner = inner((String) parsed.get(message), depth + 1, drain);
            if (inner != null) {
                signature += " > " + message + ": " + inner;
            }
        }
        return signature;
    }

    // A value that reads as a sentence or message rather than a code or name.
    private static boolean freeText(final Object value) {
        return value instanceof final String text && PyText.len(text) >= 40 && PyText.count(text, " ") >= 5;
    }

    // The shape of a message field's content: JSON in it, naming key=value pairs, or (only when nothing else
    // names the event) its masked text.
    private String inner(final String value, final int depth, final boolean drain) {
        final String text = PyText.strip(value.replace("﻿", ""));
        final int start = text.indexOf('{');
        if (start >= 0) {
            Object json = null;
            try {
                json = PyJson.loads(text.substring(start));
            } catch (final PyJson.JsonDecodeException e) {
                // Not JSON.
            }
            if (json instanceof Map) {
                return "json:" + fields(SampleProfiler.flatten(json), depth);
            }
        }
        final Set<String> pairs = new TreeSet<>(PyText::compare);
        final List<String[]> found = new ArrayList<>(PyText.findallGroups(KV, text));
        found.addAll(PyText.findallGroups(BRACKETED, text));
        for (final String[] pair : found) {
            if (PyText.search(NAMING, pair[0]) && PyText.len(pair[1]) <= 60) {
                pairs.add(pair[0] + "=" + PyText.strip(pair[1]));
            }
        }
        if (!pairs.isEmpty()) {
            return "kv:" + String.join(", ", pairs);
        }
        return drain
                ? text("", text)
                : null;
    }

    private String text(final String program, final String message) {
        final List<String> tokens = mask(message);
        final List<List<String>> bucket = templates.computeIfAbsent(List.of(program, tokens.size()),
                k -> new ArrayList<>());
        for (final List<String> template : bucket) {
            int same = 0;
            for (int i = 0; i < Math.min(template.size(), tokens.size()); i++) {
                if (template.get(i).equals(tokens.get(i))) {
                    same++;
                }
            }
            if (!tokens.isEmpty() && same / (double) tokens.size() >= 0.6
                && template.getFirst().equals(tokens.getFirst())) {
                final List<String> merged = new ArrayList<>();
                for (int i = 0; i < Math.min(template.size(), tokens.size()); i++) {
                    merged.add(template.get(i).equals(tokens.get(i))
                            ? template.get(i)
                            : WILD);
                }
                final String old = "text:" + program + "|" + String.join(" ", template);
                final String renamed = "text:" + program + "|" + String.join(" ", merged);
                if (!renamed.equals(old)) {
                    rename(old, renamed);
                }
                template.clear();
                template.addAll(merged);
                return renamed;
            }
        }
        bucket.add(new ArrayList<>(tokens));
        return "text:" + program + "|" + String.join(" ", tokens);
    }

    // A text template widened: rename the shapes that end in it (a message inside a record ends its signature),
    // merging into a shape that already has the new name.
    @SuppressWarnings("unchecked")
    private void rename(final String old, final String renamed) {
        for (final String key : new ArrayList<>(shapes.keySet())) {
            if (!key.endsWith(old)) {
                continue;
            }
            final Map<String, Object> shape = shapes.remove(key);
            final String name = key.substring(0, key.length() - old.length()) + renamed;
            final Map<String, Object> into = shapes.get(name);
            if (into == null) {
                final Map<String, Object> moved = new LinkedHashMap<>(shape);
                moved.put("signature", name);
                shapes.put(name, moved);
                continue;
            }
            into.put("count", (int) into.get("count") + (int) shape.get("count"));
            into.put("known", (boolean) into.get("known") || (boolean) shape.get("known"));
            final List<Long> streams = (List<Long>) into.get("streams");
            for (final Long stream : (List<Long>) shape.get("streams")) {
                if (!streams.contains(stream)) {
                    streams.add(stream);
                }
            }
            final List<Map<String, Object>> kept = new ArrayList<>((List<Map<String, Object>>) into.get("examples"));
            kept.addAll((List<Map<String, Object>>) shape.get("examples"));
            into.put("examples", new ArrayList<>(kept.subList(0, Math.min(examples, kept.size()))));
        }
    }

    /// Counts a record. Examples keep where the record is (stream, part, record index), so it can be stepped in
    /// place.
    ///
    /// @param chunk    A part's records.
    /// @param index    The record's index.
    /// @param streamId The stream it is in.
    /// @param part     The part it is in.
    /// @return True if it started a shape not seen before (known ones included).
    @SuppressWarnings("unchecked")
    public boolean add(final RecordSplitter.Chunk chunk, final int index, final long streamId, final int part) {
        final String signature = signature(chunk, index);
        final boolean isNew = !shapes.containsKey(signature);
        final Map<String, Object> shape = addShape(signature, false);
        shape.put("count", (int) shape.get("count") + 1);
        final List<Long> streams = (List<Long>) shape.get("streams");
        if (!streams.contains(streamId)) {
            streams.add(streamId);
        }
        final String text = chunk.records().get(index);
        final List<Map<String, Object>> kept = (List<Map<String, Object>>) shape.get("examples");
        if (kept.size() < examples && kept.stream().noneMatch(e -> text.equals(e.get("text")))) {
            kept.add(SampleProfiler.map("text", text, "location",
                    SampleProfiler.map("stream", streamId, "part", part, "record", index)));
        }
        return isNew;
    }

    /// As [#add(RecordSplitter.Chunk, int, long, int)], for part 0.
    ///
    /// @param chunk    A part's records.
    /// @param index    The record's index.
    /// @param streamId The stream it is in.
    /// @return True if it started a shape not seen before (known ones included).
    public boolean add(final RecordSplitter.Chunk chunk, final int index, final long streamId) {
        return add(chunk, index, streamId, 0);
    }

    /// Masks a message's variable parts: quoted strings, e-mail and IP addresses, UUIDs, long hex numbers, and any
    /// word with a digit in it.
    ///
    /// @param message A message.
    /// @return Its words, masked.
    public static List<String> mask(final String message) {
        String masked = message;
        for (final Map.Entry<Pattern, String> entry : MASKS) {
            masked = PyText.sub(entry.getKey(), entry.getValue(), masked);
        }
        final List<String> tokens = new ArrayList<>();
        for (final String token : PyText.split(masked)) {
            tokens.add(PyText.fullmatch(TOKEN_WITH_DIGIT, token) && !token.startsWith("<")
                    ? "<n>"
                    : token);
        }
        return tokens;
    }

    // (program, message) for syslog lines; ("", line) otherwise.
    private static String[] syslogParts(final String line) {
        Matcher m = PyText.matchAt(SYSLOG_5424_PARTS, line);
        if (m == null) {
            m = PyText.matchAt(SYSLOG_3164_PARTS, line);
        }
        return m != null
                ? new String[]{m.group(1), m.group(2)}
                : new String[]{"", line};
    }

    /// A CEF record's header fields (after any syslog prefix), its pipes escaped as `\|` inside a field.
    ///
    /// @param line A line.
    /// @return `version`, `vendor`, `product`, `device_version`, `signature_id`, `name` and `severity`, or null when
    /// the line has no whole CEF header.
    public static Map<String, String> cefHeader(final String line) {
        final int start = line.indexOf("CEF:");
        if (start < 0) {
            return null;
        }
        final List<String> fields = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (final char ch : line.substring(start + 4).toCharArray()) {
            if (escaped) {
                current.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '|' && fields.size() < CEF_FIELDS.size()) {
                fields.add(PyText.strip(current.toString()));
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (fields.size() != CEF_FIELDS.size()) {
            return null;
        }
        final Map<String, String> header = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            header.put(CEF_FIELDS.get(i), fields.get(i));
        }
        return header;
    }

    /// @param counts Counts by key.
    /// @param key    A key.
    /// @return The key's share of the counts, as a percentage to one decimal place.
    public static double share(final Map<String, ? extends Number> counts, final String key) {
        final long total = counts.values().stream().mapToLong(Number::longValue).sum();
        final Number count = counts.get(key);
        return total == 0
                ? 0.0
                : PyText.round(100.0 * (count == null
                        ? 0
                        : count.longValue()) / total, 1);
    }
}
