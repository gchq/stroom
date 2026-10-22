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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.fielddoc.PythonStrings;
import stroom.ai.impl.tools.generation.XmlComment;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlInstruction;
import stroom.ai.impl.tools.generation.XmlNode;
import stroom.ai.impl.tools.generation.XmlParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// CEF lines another pipeline writes (or the plan's XSLT wrote): read from a pipeline element's output, parsed
/// (the header and the key=value pairs, unescaped), and reviewed against ArcSight's dictionary and the Events they
/// were made from. Ported from the Stroom MCP server's `lines_in`, `parse`, `review` and `examples_from` in
/// `utils/cef.py`.
public final class CefLines {

    private static final Pattern DECLARATION = Pattern.compile("<\\?xml[^>]*\\?>");
    private static final Pattern CEF_START = PythonStrings.re("CEF:\\d+\\|");
    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");
    private static final Pattern SHIFTED = PythonStrings.re("(\\d{1,2}|Low|Medium|High|Very-High)\\|");
    private static final Pattern MILLIS = PythonStrings.re("\\d{10,13}");
    private static final Pattern CEF_DATE = PythonStrings.re("[A-Z][a-z]{2} \\d{2} \\d{4} .*");
    private static final Set<String> KAFKA_RECORDS = Set.of("kafka-records:1", "kafka-records:2");
    private static final Set<String> NUMERIC = Set.of("ip", "int", "long", "float", "mac");

    private CefLines() {
        // Static only.
    }

    /// `lines_in`: CEF lines in a pipeline element's output: Kafka records' values, or lines of text.
    ///
    /// @param output The output, or null.
    /// @return The CEF lines.
    public static List<String> linesIn(final String output) {
        String text = output == null
                ? ""
                : output;
        if (text.contains("<kafkaRecord")) {
            try {
                final XmlElement root = XmlParser.parse(text);
                final List<String> values = new ArrayList<>();
                for (final XmlElement value : root.iterElements()) {
                    if ("value".equals(value.localName()) && KAFKA_RECORDS.contains(value.namespace())
                        && value.parent() != null && "kafkaRecord".equals(value.parent().localName())) {
                        values.add(itertext(value));
                    }
                }
                return values.stream().filter(v -> v.contains("CEF:")).toList();
            } catch (final XmlParser.XmlParseException e) {
                // Read as text.
            }
        }
        // Stroom's stepping puts an XML declaration before a text XSLT's output (seen: '<?xml version="1.1"
        // encoding="UTF-8"?>CEF:0|...'): not part of the line. A syslog prefix is, and stays.
        text = DECLARATION.matcher(text).replaceAll("");
        return PythonStrings.splitlines(text).stream()
                .filter(line -> PythonStrings.strip(line).startsWith("CEF:") || CEF_START.matcher(line).find())
                .map(PythonStrings::strip)
                .toList();
    }

    /// `parse`: a CEF line's header and extension.
    ///
    /// @param line A CEF line.
    /// @return Its header (`CEF:n`, vendor, product, version, class id, name, severity) and its key=value pairs,
    /// unescaped.
    public static Parsed parse(final String line) {
        final Header header = splitHeader(line);
        final List<Map.Entry<String, String>> pairs = new ArrayList<>();
        for (final String[] pair : pairs(header.rest())) {
            final Matcher matcher = ESCAPE.matcher(pair[1]);
            final StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                final String escaped = matcher.group(1);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(switch (escaped) {
                    case "n" -> "\n";
                    case "r" -> "\r";
                    default -> escaped;
                }));
            }
            matcher.appendTail(sb);
            pairs.add(Map.entry(pair[0], sb.toString()));
        }
        return new Parsed(header.fields(), pairs);
    }

    /// `review`: what is wrong with these CEF lines, how much of each Event they carry, and the mapping they imply.
    ///
    /// @param lines      The CEF lines.
    /// @param events     The Events the lines were made from, in the same order (none if not known).
    /// @param customKeys Whether keys outside the dictionary are allowed (false), or null if not known.
    /// @return `lines` (how many), `problems` (each with how many lines and an example), `implied_mapping` (per kind
    /// of event, each key's path, label and how often), `not_sent` (Event values no key carried).
    public static Map<String, Object> review(final List<String> lines,
                                             final List<XmlElement> events,
                                             final Boolean customKeys) {
        final Review review = new Review();
        for (int n = 0; n < lines.size(); n++) {
            review.line(lines.get(n), n < events.size()
                    ? events.get(n)
                    : null, customKeys);
        }
        return review.result(lines.size());
    }

    /// `examples_from`: an example value of each key, from sampled lines.
    ///
    /// @param plan   The plan the lines were written by.
    /// @param lines  The lines.
    /// @param events The Events they were made from, in the same order.
    /// @return The first value of each (kind of event or `common`, key), and each header field's as (`header`,
    /// name).
    public static Map<CefDocs.KindKey, String> examplesFrom(final CefPlan plan, final List<String> lines,
                                                           final List<XmlElement> events) {
        final Map<CefDocs.KindKey, String> out = new LinkedHashMap<>();
        final Set<String> common = new TreeSet<>(plan.common().stream().map(CefField::key).toList());
        for (int n = 0; n < lines.size(); n++) {
            final Parsed parsed = parse(lines.get(n));
            final String kind = Objects.requireNonNullElse(n < events.size()
                    ? CefEvents.eventKind(events.get(n))
                    : null, "other");
            for (final Map.Entry<String, String> pair : parsed.extension()) {
                out.putIfAbsent(new CefDocs.KindKey(common.contains(pair.getKey())
                        ? "common"
                        : kind, pair.getKey()), pair.getValue());
            }
            for (int i = 0; i < CefKeys.HEADER.size() && i + 1 < parsed.header().size(); i++) {
                out.putIfAbsent(new CefDocs.KindKey("header", CefKeys.HEADER.get(i).name()),
                        parsed.header().get(i + 1));
            }
        }
        return out;
    }

    // _split_header: the header's seven fields, unescaped, and the extension after them ("" if there aren't seven).
    private static Header splitHeader(final String line) {
        final int[] cps = line.codePoints().toArray();
        if (cps.length == 0) {
            return new Header(List.of(), "");
        }
        final String prefix = "CEF:";
        final int found = line.indexOf(prefix);
        // Python starts at -1 (its last character) when there is no CEF: in the line.
        int i = found < 0
                ? -1
                : line.codePointCount(0, found);
        final List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        while (i < cps.length && fields.size() < 7) {
            final int ch = cps[i < 0
                    ? cps.length + i
                    : i];
            if (ch == '\\' && i + 1 < cps.length) {
                current.appendCodePoint(cps[i + 1 < 0
                        ? cps.length + i + 1
                        : i + 1]);
                i += 2;
                continue;
            }
            if (ch == '|') {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.appendCodePoint(ch);
            }
            i++;
        }
        final String rest = fields.size() == 7
                ? new String(cps, Math.max(0, i), cps.length - Math.max(0, i))
                : "";
        return new Header(fields, rest);
    }

    // The extension's pairs, as `([A-Za-z0-9_.]+)=((?:[^\\]|\\.)*?)(?=\s+[A-Za-z0-9_.]+=|\s*$)` finds them (a value
    // runs to the next ' key=', which an escaped = can't start), scanned rather than matched so a long value can't
    // overflow the regex engine's stack.
    private static List<String[]> pairs(final String rest) {
        final List<String[]> out = new ArrayList<>();
        int at = 0;
        while (at < rest.length()) {
            final int keyEnd = keyRun(rest, at);
            if (keyEnd == at || keyEnd >= rest.length() || rest.charAt(keyEnd) != '=') {
                at++;
                continue;
            }
            final int valueEnd = valueEnd(rest, keyEnd + 1);
            if (valueEnd < 0) {
                at++;
                continue;
            }
            out.add(new String[]{rest.substring(at, keyEnd), rest.substring(keyEnd + 1, valueEnd)});
            at = valueEnd;
        }
        return out;
    }

    // Where a value starting at `from` ends: the first place another key follows (after white space) or only white
    // space is left; -1 if a backslash with nothing after it stops it first.
    private static int valueEnd(final String text, final int from) {
        int i = from;
        while (true) {
            if (nextKeyAt(text, i) || blankFrom(text, i)) {
                return i;
            }
            if (i >= text.length()) {
                return -1;
            }
            if (text.charAt(i) == '\\') {
                if (i + 1 >= text.length()) {
                    return -1;
                }
                i += 2;
            } else {
                i++;
            }
        }
    }

    private static boolean nextKeyAt(final String text, final int at) {
        int i = at;
        while (i < text.length() && PythonStrings.isSpace(text.charAt(i))) {
            i++;
        }
        if (i == at) {
            return false;
        }
        final int keyEnd = keyRun(text, i);
        return keyEnd > i && keyEnd < text.length() && text.charAt(keyEnd) == '=';
    }

    private static boolean blankFrom(final String text, final int at) {
        for (int i = at; i < text.length(); i++) {
            if (!PythonStrings.isSpace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int keyRun(final String text, final int from) {
        int i = from;
        while (i < text.length() && isKeyChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isKeyChar(final char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.';
    }

    // lxml's itertext(): the text of an element and every element below it, and their tails, in order.
    private static String itertext(final XmlElement element) {
        final StringBuilder sb = new StringBuilder(Objects.requireNonNullElse(element.text(), ""));
        for (final XmlNode child : element.children()) {
            if (child instanceof final XmlElement sub) {
                sb.append(itertext(sub));
            }
            if (child instanceof XmlElement || child instanceof XmlComment || child instanceof XmlInstruction) {
                sb.append(Objects.requireNonNullElse(child.tail(), ""));
            }
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A parsed CEF line.
    ///
    /// @param header    `CEF:n`, vendor, product, version, class id, name, severity (fewer if the line is short of
    ///                  them).
    /// @param extension The key=value pairs, in order, unescaped.
    public record Parsed(List<String> header, List<Map.Entry<String, String>> extension) {

        /// @return It as the Python dict: `header` and `extension` (pairs as two-item lists).
        public Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("header", header);
            out.put("extension", extension.stream().map(e -> List.of(e.getKey(), e.getValue())).toList());
            return out;
        }
    }

    private record Header(List<String> fields, String rest) {

    }

    // A review's tallies, line by line.
    private static final class Review {

        private final Map<String, Integer> problems = new LinkedHashMap<>();
        private final Map<String, String> examples = new LinkedHashMap<>();
        private final Map<List<String>, Map<String, Integer>> implied = new LinkedHashMap<>();
        private final Map<List<String>, String> labels = new LinkedHashMap<>();
        private final Map<List<String>, Integer> unsent = new LinkedHashMap<>();
        private final Map<String, Integer> kinds = new LinkedHashMap<>();

        private void flag(final String what, final String example) {
            problems.merge(what, 1, Integer::sum);
            examples.putIfAbsent(what, PythonStrings.head(example, 300));
        }

        private void line(final String line, final XmlElement event, final Boolean customKeys) {
            if (PythonStrings.strip(line).contains("\n")) {
                flag("a raw line break inside a CEF line (escape it as \\n)", line);
            }
            final Parsed parsed = parse(line);
            final List<String> header = parsed.header();
            final List<Map.Entry<String, String>> pairs = parsed.extension();
            if (header.size() != 7) {
                flag("a header of " + header.size() + " fields, where CEF has 7 (CEF:n|vendor|product|version|class "
                     + "id|name|severity), or an unescaped | in one", line);
            } else if (!CefKeys.SEVERITIES.contains(header.get(6))) {
                final String rest = splitHeader(line).rest();
                final Matcher shifted = SHIFTED.matcher(rest);
                if (shifted.lookingAt() && CefKeys.SEVERITIES.contains(shifted.group(1))) {
                    // Seen in a pipeline written by hand: 'Secret viewed: a=b|c' as the name, its | not escaped, so
                    // 'c' was read as the severity and the real one began the extension.
                    flag("an unescaped | in the header's name or class id (in '" + header.get(5) + "|" + header.get(6)
                         + "'): escape it as \\|", line);
                } else {
                    flag("severity '" + header.get(6) + "' is not 0 to 10 (or Low, Medium, High, Very-High)", line);
                }
            }
            final List<String> keys = pairs.stream().map(Map.Entry::getKey).toList();
            for (final String k : new TreeSet<>(keys.stream().filter(k -> keys.indexOf(k) != keys.lastIndexOf(k))
                    .toList())) {
                flag("key " + k + " given twice in a line", line);
            }
            final Map<String, String> present = new LinkedHashMap<>();
            pairs.forEach(p -> present.put(p.getKey(), p.getValue()));
            for (final Map.Entry<String, String> pair : pairs) {
                checkPair(pair.getKey(), pair.getValue(), present, customKeys, line);
            }
            if (event != null) {
                tally(event, header, pairs);
            }
        }

        private void checkPair(final String k, final String v, final Map<String, String> present,
                               final Boolean customKeys, final String line) {
            final String base = k.endsWith("Label")
                    ? k.substring(0, k.length() - 5)
                    : null;
            if (!CefKeys.KEYS.containsKey(k)) {
                flag("key '" + k + "' is not in ArcSight's CEF dictionary" + (Boolean.FALSE.equals(customKeys)
                        ? " (keys outside it aren't allowed: ArcSight won't index it)"
                        : ""), line);
                return;
            }
            if (base != null && !base.isEmpty() && !present.containsKey(base)) {
                flag(k + " without its " + base, line);
            }
            if (CefKeys.SLOT_KEYS.contains(k) && !present.containsKey(k + "Label")) {
                flag(k + " has no " + k + "Label: ArcSight shows it with no name", line);
            }
            final CefKeys.KeyInfo info = CefKeys.KEYS.get(k);
            if (info.maxLength() != null && PythonStrings.len(v) > info.maxLength()) {
                flag(k + " longer than its " + info.maxLength() + " characters (ArcSight cuts it)", line);
            }
            if (NUMERIC.contains(info.type()) && !CefEvents.fits(k, List.of(v))) {
                flag(k + " holds '" + PythonStrings.head(v, 40) + "', not a" + ("ip".equals(info.type())
                        ? "n IPv4 address"
                        : " " + info.type()), line);
            }
            if ("time".equals(info.type()) && !MILLIS.matcher(v).matches() && !CEF_DATE.matcher(v).matches()) {
                flag(k + " holds '" + PythonStrings.head(v, 40) + "': CEF times are milliseconds since 1970 or 'MMM "
                     + "dd yyyy HH:mm:ss'", line);
            }
        }

        // Which Event values the line carries, and by which keys.
        private void tally(final XmlElement event, final List<String> header,
                           final List<Map.Entry<String, String>> pairs) {
            final String kind = Objects.requireNonNullElse(CefEvents.eventKind(event), "other");
            kinds.merge(kind, 1, Integer::sum);
            final Map<String, String> values = CefEvents.eventValues(event);
            final Set<String> sent = new java.util.HashSet<>(header);
            pairs.forEach(p -> sent.add(p.getValue()));
            final Map<String, String> millis = new LinkedHashMap<>();
            values.forEach((path, value) -> {
                if (CefEvents.isTime(value)) {
                    final Optional<String> ms = CefEvents.epochMillis(value);
                    ms.ifPresent(m -> millis.put(path, m));
                }
            });
            values.forEach((path, value) -> {
                final List<String> carriers = pairs.stream()
                        .filter(p -> p.getValue().equals(value) || p.getValue().equals(millis.get(path)))
                        .map(Map.Entry::getKey)
                        .toList();
                if (carriers.isEmpty() && !sent.contains(value)) {
                    unsent.merge(List.of(kind, path), 1, Integer::sum);
                }
                for (final String k : carriers) {
                    implied.computeIfAbsent(List.of(kind, k), x -> new LinkedHashMap<>()).merge(path, 1,
                            Integer::sum);
                }
            });
            for (final Map.Entry<String, String> pair : pairs) {
                if (pair.getKey().endsWith("Label")) {
                    labels.put(List.of(kind, pair.getKey().substring(0, pair.getKey().length() - 5)), pair.getValue());
                }
            }
        }

        private Map<String, Object> result(final int count) {
            final Map<String, List<Map<String, String>>> mapping = new LinkedHashMap<>();
            final Map<List<String>, Map<String, Integer>> sorted = new TreeMap<>(PAIR_ORDER);
            sorted.putAll(implied);
            sorted.forEach((kindKey, paths) -> {
                final String kind = kindKey.get(0);
                final String key = kindKey.get(1);
                if (key.endsWith("Label")) {
                    return;
                }
                Map.Entry<String, Integer> best = null;
                for (final Map.Entry<String, Integer> entry : paths.entrySet()) {
                    if (best == null || entry.getValue() > best.getValue()
                        || (entry.getValue().equals(best.getValue()) && PythonStrings.len(entry.getKey())
                                                                        < PythonStrings.len(best.getKey()))) {
                        best = entry;
                    }
                }
                final Map<String, String> entry = new LinkedHashMap<>();
                entry.put("path", best.getKey());
                entry.put("key", key);
                if (labels.containsKey(kindKey)) {
                    entry.put("label", labels.get(kindKey));
                }
                entry.put("seen", best.getValue() + " of " + kinds.getOrDefault(kind, 0));
                mapping.computeIfAbsent(kind, x -> new ArrayList<>()).add(entry);
            });
            final List<String> said = problems.entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<String, Integer> e) -> -e.getValue()))
                    .map(e -> e.getKey() + " (" + e.getValue() + " line" + (e.getValue() > 1
                            ? "s"
                            : "") + "; e.g. " + examples.get(e.getKey()) + ")")
                    .toList();
            final Map<List<String>, Integer> unsentSorted = new TreeMap<>(PAIR_ORDER);
            unsentSorted.putAll(unsent);
            final List<Map<String, String>> notSent = new ArrayList<>();
            unsentSorted.forEach((kindPath, n) -> {
                final Map<String, String> entry = new LinkedHashMap<>();
                entry.put("event_type", kindPath.get(0));
                entry.put("path", kindPath.get(1));
                entry.put("events", n + " of " + kinds.getOrDefault(kindPath.get(0), 0));
                notSent.add(entry);
            });
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("lines", count);
            out.put("problems", said);
            out.put("implied_mapping", mapping);
            out.put("not_sent", notSent);
            return out;
        }

        private static final Comparator<List<String>> PAIR_ORDER = Comparator.<List<String>, String>comparing(
                p -> p.get(0)).thenComparing(p -> p.get(1));
    }
}
