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
import stroom.ai.impl.workspace.DeadlineCharSequence;
import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.impl.workspace.SafePattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A [SplitterSpec] inferred from a sample and run on it locally, as the Data Splitter generated from it would run
/// in Stroom (the dry run of the Stroom MCP server's `utils/dsgen.py`): the records as name to value, the lines that
/// match nothing, and the field names seen (what a mapping's `field` may name). The dry run follows the subset of
/// Data Splitter the generator writes, not the whole language. Its regexes run as Java regexes, as Data Splitter
/// runs them.
///
/// The generation of the Data Splitter XML itself is not here.
public final class SplitterDryRun {

    /// Kubernetes CRI container logs: `<time> <stream> <P|F> <content>`, a long message split into partial (P) lines
    /// ended by a full (F) one.
    public static final String CRI_LINE = "^\\S+ (?:stdout|stderr) [PF] ";
    /// The start of the full (F) line that ends a CRI record.
    public static final String CRI_END = "\\S+ (?:stdout|stderr) F ";
    /// The XPath joining a CRI record's parts: each line's prefix after the first taken out, with its new line.
    public static final String CRI_JOIN = "replace(data[@name='message']/@value, '\\n\\S+ (stdout|stderr) [PF] ', '')";
    /// The RFC 5424 syslog header's regex; the body is the group named message.
    public static final String SYSLOG_5424 = "^(?:<(\\d+)>)?1 (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (-|\\[.*?\\]) ?(.*)$";
    /// The names of [#SYSLOG_5424]'s groups.
    public static final List<String> SYSLOG_5424_NAMES = List.of("pri", "time", "host", "app", "pid", "msgid",
            "structured", "message");
    /// The RFC 3164 syslog header's regex. The tag (app[pid]) only before a colon: Palo Alto sends none, and its
    /// CSV's first column was read as one.
    public static final String SYSLOG_3164 = "^(?:<(\\d+)>)?([A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2}) (\\S+) "
                                             + "(?:([^:\\[ ]+)(?:\\[(\\d+)\\])?: ?)?(.*)$";
    /// The names of [#SYSLOG_3164]'s groups.
    public static final List<String> SYSLOG_3164_NAMES = List.of("pri", "time", "host", "app", "pid", "message");
    /// CEF: whatever comes before it (a syslog header), the seven header fields (a | inside one is escaped \|), then
    /// the extension.
    public static final String CEF_PATTERN = "^(.*?)CEF:(\\d+)\\|((?:\\\\.|[^|\\\\])*)\\|((?:\\\\.|[^|\\\\])*)\\|"
                                             + "((?:\\\\.|[^|\\\\])*)\\|((?:\\\\.|[^|\\\\])*)\\|"
                                             + "((?:\\\\.|[^|\\\\])*)\\|((?:\\\\.|[^|\\\\])*)\\|(.*)$";
    /// The names of the CEF header's fields.
    public static final List<String> CEF_NAMES = List.of("cef_version", "cef_vendor", "cef_product",
            "cef_device_version", "cef_signature_id", "cef_name", "cef_severity");
    /// One pair of a CEF extension: its value may hold spaces, and runs to the next ` key=` (an = inside one is
    /// escaped \=).
    public static final String CEF_PAIR = "\\s*([^=\\s]+)=((?:\\\\.|[^\\\\])*?)(?=\\s+[^=\\s]+=|\\s*$)";
    /// The syslog headers a CEF line may follow, without their message, and the names of their groups.
    public static final List<Map.Entry<String, List<String>>> CEF_PREFIXES = List.of(
            Map.entry("^(?:<(\\d+)>)?1 (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (-|\\[.*?\\])\\s*$",
                    List.of("pri", "time", "host", "app", "pid", "msgid", "structured")),
            Map.entry("^(?:<(\\d+)>)?([A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2}) (\\S+)\\s*$",
                    List.of("pri", "time", "host")),
            Map.entry("^(.*?)\\s*$", List.of("prefix")));
    /// The most records a dry run reads; a longer sample is read at lines spread over it.
    public static final int MAX_RECORDS = 2000;

    private static final Pattern CEF = Pattern.compile(CEF_PATTERN);
    private static final Pattern CEF_PAIR_PATTERN = Pattern.compile(CEF_PAIR);

    private SplitterDryRun() {
        // Static methods only.
    }

    /// @param directive A directive, e.g. `#Software:`.
    /// @return The field its value is kept in: the directive as the file writes it, without a trailing colon.
    public static String directiveField(final String directive) {
        return PyText.strip(PyText.rstrip(PyText.strip(directive), ":"));
    }

    /// The regex for a record of named columns (no header line): one group per column, quoted or not. A quoted
    /// cell's group is its text without the quotes, a doubled quote inside kept doubled (as Data Splitter's
    /// containers leave it). Seen: a value written as $1$2 from two groups, which Stroom refuses when one took no
    /// part.
    ///
    /// @param spec A delimited spec with column names.
    /// @return The regex.
    public static String namedColumnsPattern(final SplitterSpec spec) {
        final String d = PyText.escape(spec.delimiter());
        final List<String> cells = new ArrayList<>();
        if (spec.delimiter().length() > 1) {
            // A separator of several characters (' ~ ', '||'): a column runs to the next whole separator.
            spec.columns().forEach(column -> cells.add("((?:(?!" + d + ").)*)"));
            return "^" + String.join(d, cells) + "$";
        }
        final String cell;
        if (spec.quote() != null) {
            final String q = PyText.escape(spec.quote());
            cell = q + "?((?<=" + q + ")(?:[^" + q + "]|" + q + q + ")*(?=" + q + ")|(?<!" + q + ")[^" + d + q + "]*)"
                   + q + "?";
        } else {
            cell = "([^" + d + "]*)";
        }
        spec.columns().forEach(column -> cells.add(cell));
        return "^" + String.join(d, cells) + "$";
    }

    /// The regexes for one key=value pair, tried in turn as Data Splitter and the dry run both match them along the
    /// text: with a quote character, one group for the value whether quoted or not (a lookbehind says which), so it
    /// always takes part.
    ///
    /// @param spec A key_value spec.
    /// @return The regexes.
    public static List<String> kvPatterns(final SplitterSpec spec) {
        final String d = PyText.isBlank(spec.delimiter())
                ? "\\s"
                : PyText.escape(spec.delimiter());
        final String s = PyText.escape(spec.pairSeparator());
        final String key = "[^" + s + d + "]+";
        if (spec.quote() != null) {
            // Stroom neither tries a group's regexes in turn at each place nor lets a value name a group that didn't
            // match.
            final String q = PyText.escape(spec.quote());
            return List.of(d + "*(" + key + ")" + s + q + "?((?<=" + q + ")[^" + q + "]*(?=" + q + ")|(?<!" + q
                           + ")[^" + d + q + "]*)" + q + "?");
        }
        return List.of(d + "*(" + key + ")" + s + "([^" + d + "]*)");
    }

    /// Splits a line at a delimiter, leaving it inside quotes alone; a doubled quote inside a quoted value stays
    /// doubled, as Data Splitter leaves it (seen in Stroom: `"Said ""bye"""` is `Said ""bye""`).
    ///
    /// @param line      The line.
    /// @param delimiter The delimiter.
    /// @param quote     The quote character, or null.
    /// @return The cells, without their quotes.
    public static List<String> splitQuoted(final String line, final String delimiter, final String quote) {
        if (quote == null || quote.isEmpty() || !line.contains(quote)) {
            return PyText.split(line, delimiter);
        }
        final List<String> out = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean inside = false;
        int i = 0;
        while (i < line.length()) {
            if (inside && line.startsWith(quote + quote, i)) {
                // The mapping's unescape_quotes transform makes it one.
                cell.append(quote).append(quote);
                i += 2 * quote.length();
                continue;
            }
            if (line.startsWith(quote, i)) {
                inside = !inside;
            } else if (!inside && line.startsWith(delimiter, i)) {
                out.add(cell.toString());
                cell.setLength(0);
                i += delimiter.length();
                continue;
            } else {
                cell.append(line.charAt(i));
            }
            i++;
        }
        out.add(cell.toString());
        return out;
    }

    /// One record's fields, as the generated Data Splitter would write them.
    ///
    /// @param spec    The spec.
    /// @param text    The record's text.
    /// @param heading The column names a header line gave, for a delimited spec with one; else null.
    /// @return The fields, or null when the record doesn't match (no data elements would be written).
    public static Map<String, String> applyRecord(final SplitterSpec spec, final String text,
                                                  final List<String> heading) {
        SplitterSpec applied = spec;
        if ("syslog".equals(spec.kind())) {
            final boolean rfc5424 = "rfc5424".equals(spec.rfc());
            applied = SplitterSpec.builder("regex")
                    .pattern(rfc5424
                            ? SYSLOG_5424
                            : SYSLOG_3164)
                    .names(rfc5424
                            ? SYSLOG_5424_NAMES
                            : SYSLOG_3164_NAMES)
                    .body(spec.body())
                    .bodyField(spec.bodyField())
                    .build();
        }
        if ("cef".equals(applied.kind())) {
            return applyCef(applied, text);
        }
        Map<String, String> fields;
        if ("regex".equals(applied.kind())) {
            final Matcher m = SafePattern.matcher(SafePattern.compile(applied.pattern(), 0), text);
            if (!m.lookingAt()) {
                return null;
            }
            fields = new LinkedHashMap<>();
            for (int n = 1; n <= applied.names().size(); n++) {
                final String name = applied.names().get(n - 1);
                if (!name.isEmpty() && n <= m.groupCount()) {
                    fields.put(name, m.group(n) == null
                            ? ""
                            : m.group(n));
                }
            }
        } else if ("key_value".equals(applied.kind())) {
            fields = applyKeyValue(applied, text);
            if (fields.isEmpty()) {
                return null;
            }
        } else if (applied.headerLine()) {
            final List<String> cells = splitQuoted(text, applied.delimiter(), applied.quote());
            fields = new LinkedHashMap<>();
            for (int n = 0; n < cells.size(); n++) {
                if (heading != null && n < heading.size()) {
                    fields.put(heading.get(n), cells.get(n));
                }
            }
        } else {
            // The same regex the converter runs.
            final Matcher m = SafePattern.matcher(Pattern.compile(namedColumnsPattern(applied)), text);
            if (!m.lookingAt()) {
                return null;
            }
            fields = new LinkedHashMap<>();
            for (int n = 0; n < Math.min(applied.columns().size(), m.groupCount()); n++) {
                fields.put(applied.columns().get(n), m.group(n + 1));
            }
        }
        if (applied.body() != null && fields.containsKey(applied.bodyField())) {
            final String bodyText = fields.get(applied.bodyField());
            final Map<String, String> inner = applyRecord(applied.body(), bodyText, null);
            if ((inner == null || inner.isEmpty()) && bodyText != null && !PyText.isBlank(bodyText)) {
                // Stroom reports a body its expressions don't match ("Expressions failed to match all of the
                // content"): so does the dry run, rather than keeping the record without the body's fields (seen: a
                // Palo Alto body short of a column, fine here and refused in Stroom).
                return null;
            }
            if (inner != null && !inner.isEmpty()) {
                fields = new LinkedHashMap<>(fields);
                fields.putAll(inner);
            }
        }
        return fields;
    }

    private static Map<String, String> applyCef(final SplitterSpec spec, final String text) {
        final Matcher m = SafePattern.matcher(CEF, text);
        if (!m.lookingAt()) {
            return null;
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        for (int n = 0; n < CEF_NAMES.size(); n++) {
            fields.put(CEF_NAMES.get(n), m.group(n + 2));
        }
        final Matcher pair = SafePattern.matcher(CEF_PAIR_PATTERN, m.group(9));
        while (pair.find()) {
            fields.put(pair.group(1), pair.group(2));
        }
        if (spec.body() != null && !PyText.isBlank(m.group(1))) {
            final Map<String, String> prefixed = new LinkedHashMap<>();
            final Map<String, String> prefix = applyRecord(spec.body(), m.group(1), null);
            if (prefix != null) {
                prefixed.putAll(prefix);
            }
            prefixed.putAll(fields);
            return prefixed;
        }
        return fields;
    }

    // The regexes the converter runs, matched along the text as Data Splitter does: at each place the first that
    // matches.
    private static Map<String, String> applyKeyValue(final SplitterSpec spec, final String text) {
        final List<Pattern> patterns = new ArrayList<>();
        kvPatterns(spec).forEach(p -> patterns.add(Pattern.compile(p)));
        final Map<String, String> fields = new LinkedHashMap<>();
        int at = 0;
        while (at < text.length()) {
            Matcher found = null;
            for (final Pattern pattern : patterns) {
                final Matcher m = PyText.matchFrom(pattern, new DeadlineCharSequence(text), at);
                if (m != null && m.end() > at) {
                    found = m;
                    break;
                }
            }
            if (found == null) {
                break;
            }
            // A key given again (rcpt= once per recipient) is several data elements in Stroom; a field reads the
            // first (repeat: true writes each), so the dry run keeps the first, not the last.
            fields.putIfAbsent(found.group(1), found.group(2));
            at = found.end();
        }
        return fields;
    }

    /// @param messages Syslog messages' bodies.
    /// @return How they divide further, when they do: key=value pairs, or delimited columns (as Palo Alto firewalls
    /// send them: a syslog header, then their CSV), named by position for the model to rename; else null.
    static SplitterSpec bodySpec(final List<String> messages) {
        final List<String> bodies = new ArrayList<>();
        for (final String message : messages) {
            if (!PyText.isBlank(message)) {
                bodies.add(message);
            }
        }
        int pairs = 0;
        for (final String body : bodies) {
            if (PyText.findallGroups(SampleProfiler.KEY_VALUE, body).size() >= 2) {
                pairs++;
            }
        }
        if (!bodies.isEmpty() && pairs >= 0.8 * bodies.size()) {
            final boolean quoted = messages.stream().anyMatch(message -> message.contains("=\""));
            return SplitterSpec.builder("key_value").delimiter(" ").quote(quoted
                    ? "\""
                    : null).build();
        }
        for (final String delimiter : List.of(",", "|", "\t", ";")) {
            final String quote = bodies.stream().anyMatch(body -> body.contains("\""))
                    ? "\""
                    : null;
            final List<Integer> widths = new ArrayList<>();
            bodies.forEach(body -> widths.add(splitQuoted(body, delimiter, quote).size()));
            if (!widths.isEmpty() && widths.stream().mapToInt(Integer::intValue).min().orElse(0) >= 6
                && widths.stream().filter(w -> w.equals(widths.getFirst())).count() >= 0.8 * widths.size()) {
                // Columns named by position: a vendor's own names (its documentation) are the model's to give.
                final int most = widths.stream().mapToInt(Integer::intValue).max().orElse(0);
                final List<String> columns = new ArrayList<>();
                for (int i = 1; i <= most; i++) {
                    columns.add("col" + i);
                }
                return SplitterSpec.builder("delimited").delimiter(delimiter).quote(quote).header(columns).build();
            }
        }
        return null;
    }

    /// Infers a spec for a sample's format, from its profile.
    ///
    /// @param sample The sample.
    /// @return The spec (null when the format needs no text converter, JSON or XML, or can't be inferred: free
    /// text, which needs a regex from a human) and the sample's profile.
    public static Inferred inferSpec(final String sample) {
        final Map<String, Object> info = SampleProfiler.profile(sample);
        final String format = (String) info.get("format");
        final List<String> lines = PyText.nonBlankLines(sample);
        final boolean delimited = SampleProfiler.DELIMITED.equals(format);
        if (delimited && info.get("header_directive") != null) {
            return new Inferred(SplitterSpec.builder("delimited")
                    .delimiter((String) info.get("delimiter"))
                    .header(true)
                    .quote((String) info.get("quote"))
                    .commentPrefix((String) info.get("comment_prefix"))
                    .headerDirective((String) info.get("header_directive"))
                    .directives(JsonValues.strings(info.get("directives")))
                    .build(), info);
        }
        if (delimited) {
            final boolean quotedNewlines = Boolean.TRUE.equals(info.get("quoted_newlines"));
            final String quote = quotedNewlines || lines.subList(0, Math.min(50, lines.size())).stream()
                    .anyMatch(line -> line.contains("\""))
                    ? "\""
                    : null;
            final SplitterSpec.Builder builder = SplitterSpec.builder("delimited")
                    .delimiter((String) info.getOrDefault("delimiter", ","))
                    .quote(quote)
                    .quotedNewlines(quotedNewlines);
            if (Boolean.TRUE.equals(info.get("has_header"))) {
                builder.header(true);
            } else {
                builder.header(JsonValues.strings(info.get("columns")));
            }
            return new Inferred(builder.build(), info);
        }
        if (SampleProfiler.KEY_VALUE_FORMAT.equals(format)) {
            final boolean quoted = lines.subList(0, Math.min(50, lines.size())).stream()
                    .anyMatch(line -> line.contains("=\""));
            return new Inferred(SplitterSpec.builder("key_value").delimiter(" ").pairSeparator("=").quote(quoted
                    ? "\""
                    : null).build(), info);
        }
        if (SampleProfiler.CEF_FORMAT.equals(format)) {
            return new Inferred(cefSpec(lines), info);
        }
        if (SampleProfiler.CRI.equals(format)) {
            // Kubernetes CRI: a record ends at its 'F' line; the parts after the first carry their own prefixes,
            // which the mapping takes out to join them (CRI_JOIN).
            return new Inferred(SplitterSpec.builder("regex")
                    .recordEnd(CRI_END)
                    .pattern("(?s)^(\\S+) (stdout|stderr) [PF] (.*)$")
                    .names(List.of("time", "stream", "message"))
                    .build(), info);
        }
        if (SampleProfiler.MULTI_LINE.equals(format)) {
            // The start and the time it begins with; the rest of the record is the message, new lines and all: the
            // record's own pattern (level, thread, logger...) is the model's to give, checked by the dry run.
            final String start = (String) info.get("record_start");
            return new Inferred(SplitterSpec.builder("regex")
                    .recordStart(start)
                    .pattern("(?s)^(" + start + "\\S*)\\s+(.*)$")
                    .names(List.of("time", "message"))
                    .build(), info);
        }
        if (SampleProfiler.SYSLOG_3164_FORMAT.equals(format) || SampleProfiler.SYSLOG_5424_FORMAT.equals(format)) {
            final String rfc = format.endsWith("3164")
                    ? "rfc3164"
                    : "rfc5424";
            final SplitterSpec head = SplitterSpec.builder("syslog").rfc(rfc).build();
            final List<String> messages = new ArrayList<>();
            dryRun(head, sample).records().forEach(record -> messages.add(record.getOrDefault("message", "")));
            return new Inferred(SplitterSpec.builder("syslog").rfc(rfc).body(bodySpec(messages)).build(), info);
        }
        return new Inferred(null, info);
    }

    // A syslog header before CEF: the first header pattern that fits every line's text before CEF.
    private static SplitterSpec cefSpec(final List<String> lines) {
        final List<String> prefixes = new ArrayList<>();
        for (final String line : lines) {
            final Matcher m = CEF.matcher(line);
            if (m.lookingAt()) {
                prefixes.add(m.group(1));
            }
        }
        SplitterSpec body = null;
        if (prefixes.stream().anyMatch(prefix -> !PyText.isBlank(prefix))) {
            Map.Entry<String, List<String>> chosen = CEF_PREFIXES.getLast();
            for (final Map.Entry<String, List<String>> candidate : CEF_PREFIXES) {
                final Pattern pattern = Pattern.compile(candidate.getKey());
                if (prefixes.stream().allMatch(prefix -> pattern.matcher(prefix).lookingAt())) {
                    chosen = candidate;
                    break;
                }
            }
            body = SplitterSpec.builder("regex").pattern(chosen.getKey()).names(chosen.getValue()).build();
        }
        return SplitterSpec.builder("cef").body(body).build();
    }

    /// What the generated splitter makes of a sample, reading at most [#MAX_RECORDS] records.
    ///
    /// @param spec   The spec.
    /// @param sample The sample.
    /// @return The records, the lines that match nothing, the field names seen, and the heading.
    public static DryRun dryRun(final SplitterSpec spec, final String sample) {
        return dryRun(spec, sample, MAX_RECORDS);
    }

    /// What the generated splitter makes of a sample. A longer sample is read at lines spread over it: seen in an
    /// eval case (a day's CEF file), the first 200 lines held only two kinds of event, and a mapping's field from a
    /// later kind was refused as in none of the sample's records.
    ///
    /// @param spec       The spec.
    /// @param sample     The sample.
    /// @param maxRecords The most records to read.
    /// @return The records, the lines that match nothing, the field names seen, and the heading.
    /// @throws PatternTimeoutException if the spec's patterns take too long to match.
    public static DryRun dryRun(final SplitterSpec spec, final String sample, final int maxRecords) {
        return SafePattern.limited(patterns(spec), () -> dryRunLimited(spec, sample, maxRecords));
    }

    // The patterns a spec gives, for the message if they take too long.
    private static String patterns(final SplitterSpec spec) {
        final List<String> patterns = new ArrayList<>();
        for (SplitterSpec part = spec; part != null; part = part.body()) {
            for (final String pattern : Arrays.asList(part.recordStart(), part.recordEnd(), part.pattern())) {
                if (pattern != null && !patterns.contains(pattern)) {
                    patterns.add(pattern);
                }
            }
        }
        return patterns.isEmpty()
                ? null
                : String.join("', '", patterns);
    }

    private static DryRun dryRunLimited(final SplitterSpec spec, final String sample, final int maxRecords) {
        List<String> lines = PyText.nonBlankLines(sample);
        final boolean delimited = "delimited".equals(spec.kind());
        if (spec.recordStart() != null && !delimited) {
            lines = groupLines(lines, spec.recordStart());
        } else if (spec.recordEnd() != null && !delimited) {
            lines = groupLinesToEnd(lines, spec.recordEnd());
        }
        if (delimited && spec.quotedNewlines() && spec.quote() != null) {
            lines = quotedLines(sample, spec.quote());
        }
        if (delimited && spec.commentPrefix() != null) {
            return dryRunDirectives(spec, lines, maxRecords);
        }
        List<String> heading = null;
        if (delimited && spec.headerLine() && !lines.isEmpty()) {
            heading = new ArrayList<>();
            for (final String cell : splitQuoted(lines.getFirst(), spec.delimiter(), spec.quote())) {
                heading.add(PyText.strip(cell));
            }
            lines = lines.subList(1, lines.size());
        }
        lines = spread(lines, maxRecords);
        final List<Map<String, String>> records = new ArrayList<>();
        final List<String> unmatched = new ArrayList<>();
        for (final String line : lines) {
            final Map<String, String> fields = applyRecord(spec, line, heading);
            if (fields != null && !fields.isEmpty()) {
                records.add(fields);
            } else {
                unmatched.add(PyText.head(line, 200));
            }
        }
        return new DryRun(records, unmatched, fieldNames(records), heading);
    }

    // As many items as are wanted, spread over them, when there are more.
    private static <T> List<T> spread(final List<T> items, final int most) {
        if (items.size() <= most) {
            return items;
        }
        final List<T> picked = new ArrayList<>();
        for (int i = 0; i < most; i++) {
            picked.add(items.get((int) PyText.round(i * (items.size() - 1) / (double) (most - 1))));
        }
        return picked;
    }

    private static List<String> fieldNames(final List<? extends Map<String, ?>> records) {
        final List<String> names = new ArrayList<>();
        for (final Map<String, ?> record : records) {
            for (final String name : record.keySet()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /// Records of delimited text whose quoted values may hold new lines: lines joined while a quote is open.
    ///
    /// @param text  The text.
    /// @param quote The quote character.
    /// @return The records.
    public static List<String> quotedLines(final String text, final String quote) {
        final List<String> records = new ArrayList<>();
        String current = null;
        for (final String line : PyText.splitlines(text)) {
            if (current == null) {
                if (PyText.isBlank(line)) {
                    continue;
                }
                current = line;
            } else {
                current = current + "\n" + line;
            }
            if (PyText.count(current, quote) % 2 == 0) {
                records.add(current);
                current = null;
            }
        }
        if (current != null) {
            records.add(current);
        }
        return records;
    }

    /// Lines joined into records that end at a line matching `end` (an unfinished last record kept as it is).
    ///
    /// @param lines The lines.
    /// @param end   A regex the last line of a record matches at its start.
    /// @return The records.
    /// @throws PatternTimeoutException if `end` takes too long to match.
    public static List<String> groupLinesToEnd(final List<String> lines, final String end) {
        return SafePattern.limited(end, () -> groupedToEnd(lines, end));
    }

    private static List<String> groupedToEnd(final List<String> lines, final String end) {
        final Pattern ends = SafePattern.compile(end, PyText.FLAGS);
        final List<String> records = new ArrayList<>();
        final List<String> current = new ArrayList<>();
        for (final String line : lines) {
            current.add(line);
            if (PyText.match(ends, new DeadlineCharSequence(line))) {
                records.add(String.join("\n", current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            records.add(String.join("\n", current));
        }
        return records;
    }

    /// Lines joined into records: a line matching `start` begins one, the lines after it until the next continue
    /// it (lines before the first start are a record of their own, as the splitter would leave them).
    ///
    /// @param lines The lines.
    /// @param start A regex the first line of a record matches at its start.
    /// @return The records.
    /// @throws PatternTimeoutException if `start` takes too long to match.
    public static List<String> groupLines(final List<String> lines, final String start) {
        return SafePattern.limited(start, () -> grouped(lines, start));
    }

    private static List<String> grouped(final List<String> lines, final String start) {
        final Pattern begins = SafePattern.compile(start, PyText.FLAGS);
        final List<String> records = new ArrayList<>();
        for (final String line : lines) {
            if (records.isEmpty() || PyText.match(begins, new DeadlineCharSequence(line))) {
                records.add(line);
            } else {
                records.set(records.size() - 1, records.getLast() + "\n" + line);
            }
        }
        return records;
    }

    /// The dry run of a log with directive lines: read in order (the columns and directives hold for the lines
    /// after them), then records spread over it kept, as for any long sample.
    ///
    /// @param spec       A delimited spec with a comment prefix.
    /// @param lines      The log's lines.
    /// @param maxRecords The most records to keep.
    /// @return The records, the lines that match nothing, the field names seen, and the last heading.
    /// @throws PatternTimeoutException if the spec's patterns take too long to match.
    public static DryRun dryRunDirectives(final SplitterSpec spec, final List<String> lines, final int maxRecords) {
        return SafePattern.limited(patterns(spec), () -> directivesLimited(spec, lines, maxRecords));
    }

    private static DryRun directivesLimited(final SplitterSpec spec,
                                            final List<String> lines,
                                            final int maxRecords) {
        List<String> heading = null;
        final Map<String, String> held = new LinkedHashMap<>();
        List<Map<String, String>> records = new ArrayList<>();
        final List<String> unmatched = new ArrayList<>();
        for (final String line : lines) {
            if (spec.headerDirective() != null && line.startsWith(spec.headerDirective())) {
                heading = new ArrayList<>();
                for (final String cell : splitQuoted(PyText.strip(line.substring(spec.headerDirective().length())),
                        spec.delimiter(), spec.quote())) {
                    heading.add(PyText.strip(cell));
                }
                continue;
            }
            final String directive = spec.directives().stream().filter(line::startsWith).findFirst().orElse(null);
            if (directive != null) {
                held.put(directiveField(directive), PyText.strip(line.substring(directive.length())));
                continue;
            }
            if (line.startsWith(spec.commentPrefix())) {
                continue;
            }
            final Map<String, String> fields = applyRecord(spec, line, spec.headerLine()
                    ? heading
                    : null);
            if (fields != null && !fields.isEmpty()) {
                final Map<String, String> record = new LinkedHashMap<>();
                for (final String kept : spec.directives()) {
                    record.put(directiveField(kept), held.getOrDefault(directiveField(kept), ""));
                }
                record.putAll(fields);
                records.add(record);
            } else {
                unmatched.add(PyText.head(line, 200));
            }
        }
        records = spread(records, maxRecords);
        return new DryRun(records, unmatched, fieldNames(records), heading);
    }


    // --------------------------------------------------------------------------------


    /// A spec inferred from a sample.
    ///
    /// @param spec    The spec, or null when none could be inferred or none is needed.
    /// @param profile The sample's profile (see [SampleProfiler#profile(String)]).
    public record Inferred(SplitterSpec spec, Map<String, Object> profile) {

    }

    /// What a dry run made of a sample.
    ///
    /// @param records        The records, as field name to value.
    /// @param unmatchedLines The lines (their first 200 characters) that matched nothing.
    /// @param fields         The field names seen, in order.
    /// @param heading        The column names a header line gave, or null.
    public record DryRun(List<Map<String, String>> records,
                         List<String> unmatchedLines,
                         List<String> fields,
                         List<String> heading) {

        /// @return It as the Python dry run returned it: `records`, `unmatched_lines`, `fields` and `heading`.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("records", records);
            map.put("unmatched_lines", unmatchedLines);
            map.put("fields", fields);
            map.put("heading", heading);
            return map;
        }
    }
}
