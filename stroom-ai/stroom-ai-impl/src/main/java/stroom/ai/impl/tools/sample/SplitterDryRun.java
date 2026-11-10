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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.workspace.DeadlineCharSequence;
import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.impl.workspace.SafePattern;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// A [SplitterSpec] inferred from a sample and run on it locally, as the Data Splitter generated from it would run in
/// Stroom: the records as name to value, the lines that match nothing, and the field names seen (what a mapping's
/// `field` may name). The dry run follows the subset of Data Splitter the generator writes, not the whole language. Its
/// regexes run as Java regexes, as Data Splitter runs them.
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
    /// The RFC 5424 syslog header's regex; the body is the group named message. Its structured data is every
    /// element, each read to its own closing bracket: a `]` escaped (`\]`) or in quotes inside a value doesn't end it
    /// (seen in testing: `event="User [alice\] ..."`, the element read only to the escaped bracket
    /// and the rest taken for the message).
    public static final String SYSLOG_5424 = "^(?:<(\\d+)>)?1 (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) ("
                                             + SyslogHeaders.STRUCTURED_DATA + ") ?(.*)$";
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
    public static final String CEF_PATTERN = "^(.*?)CEF:(\\d+)\\|" + ("(" + ToolText.escapedRun("|") + ")\\|").repeat(6)
                                             + "(.*)$";
    /// The names of the CEF header's fields.
    public static final List<String> CEF_NAMES = List.of("cef_version", "cef_vendor", "cef_product",
            "cef_device_version", "cef_signature_id", "cef_name", "cef_severity");
    /// One pair of a CEF extension: its value may hold spaces, and runs to the next ` key=` (an = inside one is
    /// escaped \=, so a key holds no backslash: seen, `msg=policy matched a\=b` cut at `a\=` as if it were a key).
    /// Its value: runs of anything but white space or a backslash, joined by an escaped character or by white space
    /// that no ` key=` and no end follows, each run taken whole (possessive) so a long value doesn't recurse (see
    /// [ToolText#escapedRun(String)]).
    public static final String CEF_PAIR = "\\s*([^=\\s]+)=([^\\\\\\s]*+(?:(?:\\\\.|\\s++(?![^=\\s\\\\]+=|$))"
                                          + "[^\\\\\\s]*+)*+)(?=\\s+[^=\\s\\\\]+=|\\s*$)";
    /// The syslog headers a CEF line may follow, without their message, and the names of their groups.
    public static final List<Map.Entry<String, List<String>>> CEF_PREFIXES = List.of(
            Map.entry("^(?:<(\\d+)>)?1 (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (-|\\[.*?\\])\\s*$",
                    List.of("pri", "time", "host", "app", "pid", "msgid", "structured")),
            Map.entry("^(?:<(\\d+)>)?([A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2}) (\\S+)\\s*$",
                    List.of("pri", "time", "host")),
            // rsyslog's RFC 3339 header (its high-precision file format): an ISO time, the host, then any tag. Seen in
            // testing: a relay's CEF lines read with the whole header as one field, so the draft found no time (and
            // asked the user what time to give events that had one) and no host.
            Map.entry("^(?:<(\\d+)>)?(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})) "
                      + "(\\S+)(?: ([^:\\[\\s]+)(?:\\[(\\d+)\\])?:)?\\s*$",
                    List.of("pri", "time", "host", "app", "pid")),
            Map.entry("^(.*?)\\s*$", List.of("prefix")));
    /// The most records a dry run reads; a longer sample is read at lines spread over it.
    public static final int MAX_RECORDS = 2000;
    // The most saved converters' runs kept (see converterRun), the least recently used forgotten first.
    private static final int CONVERTER_RUNS_KEPT = 8;
    private static final Map<List<Object>, DryRun> CONVERTER_RUNS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<List<Object>, DryRun> eldest) {
            return size() > CONVERTER_RUNS_KEPT;
        }
    };
    /// A record that is a block of lines ended by a blank line (one of white space only, or the end): its lines
    /// without the blank ones before it. Seen in the Stroom docs' multi-line example: a query and its results.
    public static final String BLANK_LINE_RECORD = "\\s*((?:[^\\n]*\\S[^\\n]*(?:\\n|$))+)";

    private static final Pattern BLANK_LINE_RECORDS = Pattern.compile(BLANK_LINE_RECORD);

    private static final Pattern CEF = Pattern.compile(CEF_PATTERN);
    // A section of a sectioned record: its marker line, then the text kept (empty when none).
    private static final String SECTION_TEXT = "(?s)^[^\\n]*\\n?(.*)$";
    private static final String SECTION_FIELD = "text";
    // Words before a record's first pair (group 1), and the rest from that pair (group 2); the most taken as fields.
    private static final Pattern PREFIX_WORDS = Pattern.compile("((?:[^\\s=\"]+\\s+)+?)([^\\s=\"]+=.*)");
    private static final int MOST_PREFIX_WORDS = 4;
    private static final String TAB = "\t";
    // A LEEF record's start: any prefix (group 1), its version (2) and, for 2.0, the declared delimiter (3).
    private static final Pattern LEEF_HEAD = Pattern.compile(
            "^(.*?)LEEF:(\\d\\.\\d)\\|(?:" + ToolText.escapedRun("|") + "\\|){4}(?:([^|]{0,4})\\|)?");
    private static final Pattern LEEF_HEX = Pattern.compile("(?:0?x)([0-9A-Fa-f]{2})");
    private static final String LEEF_ATTRIBUTES = "leef_attributes";
    // A syslog record's structured data in brackets, read as a body where it holds the pairs (its message empty).
    private static final String BRACKETED = "(?s)^\\[(.*)\\]$";
    private static final String STRUCTURED = "structured";
    // White space between key=value pairs: any.
    private static final String WHITE_SPACE = "\\s";
    /// The variant of a header line giving the date of the lines after it.
    public static final String HEADER_VARIANT = "header";
    /// The field its date is written in.
    public static final String HEADER_DATE = "date";
    /// The field the header's date is written in on each record after it.
    public static final String CARRIED_DATE = "header_date";
    // The most parts a stream's timestamped text is read by, one per shape of its time.
    private static final int MAX_TEXT_PARTS = 6;
    private static final Pattern CEF_PAIR_PATTERN = Pattern.compile(CEF_PAIR);

    private SplitterDryRun() {
        // Static methods only.
    }

    /// @param directive A directive, e.g. `#Software:`.
    /// @return The field its value is kept in: the directive as the file writes it, without a trailing colon.
    public static String directiveField(final String directive) {
        return ToolText.strip(ToolText.rstrip(ToolText.strip(directive), ":"));
    }

    /// The regex for a record of named columns (no header line): one group per column, quoted or not. A quoted
    /// cell's group is its text without the quotes, a doubled quote inside kept doubled (as Data Splitter's
    /// containers leave it). Seen: a value written as $1$2 from two groups, which Stroom refuses when one took no
    /// part.
    ///
    /// @param spec A delimited spec with column names.
    /// @return The regex.
    public static String namedColumnsPattern(final SplitterSpec spec) {
        final String d = ToolText.escape(spec.delimiter());
        final List<String> cells = new ArrayList<>();
        if (spec.delimiter().length() > 1) {
            // A separator of several characters (' ~ ', '||'): a column runs to the next whole separator.
            spec.columns().forEach(column -> cells.add("((?:(?!" + d + ").)*)"));
            return "^" + String.join(d, cells) + "$";
        }
        final String cell;
        if (spec.quote() != null) {
            final String q = ToolText.escape(spec.quote());
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
        final String d = delimiterIn(spec);
        final String s = ToolText.escape(spec.pairSeparator());
        final String key = keyClass(spec);
        if (spec.quote() != null) {
            // Stroom neither tries a group's regexes in turn at each place nor lets a value name a group that didn't
            // match.
            // A quoted value may hold the quote escaped (msg="said \"hi\""): kept as written.
            final String q = ToolText.escape(spec.quote());
            final String quoted = ToolText.escapedRun(spec.quote());
            return List.of(lead(spec) + "(" + key + ")" + s + q + "?((?<=" + q + ")" + quoted + "(?=" + q + ")|(?<!"
                           + q + ")" + unquotedValue(d, d + q, s) + ")" + q + "?");
        }
        return List.of(lead(spec) + "(" + key + ")" + s + "(" + unquotedValue(d, d, s) + ")");
    }

    // An unquoted value: to the next delimiter, and with white space the delimiter, past it where the value ends in
    // a list's , or ; and what follows isn't a key (`rcpt=a@example.com; b@example.com subject=...`): a list of
    // values written with spaces, its items read whole rather than as words that aren't pairs. Possessive: an item
    // is read once, however long the list (seen in testing: some 300 recipients).
    private static String unquotedValue(final String delimiter, final String notIn, final String separator) {
        if (!WHITE_SPACE.equals(delimiter)) {
            return "[^" + notIn + "]*";
        }
        return "(?:[^" + notIn + "]*+(?<=[,;])\\s++(?![^" + notIn + separator + "]*+" + separator + ")(?=[^"
               + notIn + "]))*+[^" + notIn + "]*+";
    }

    // A key_value spec's delimiter as a regex, and as a character class's content. A delimiter with white space
    // round it (", ") is its other characters with any white space round them.
    private static String delimiter(final SplitterSpec spec) {
        if (TAB.equals(spec.delimiter())) {
            return "\\t";
        }
        if (spacedDelimiter(spec)) {
            return "(?:\\s*" + ToolText.escape(ToolText.strip(spec.delimiter())) + "\\s*)";
        }
        return ToolText.isBlank(spec.delimiter())
                ? WHITE_SPACE
                : ToolText.escape(spec.delimiter());
    }

    private static String delimiterIn(final SplitterSpec spec) {
        if (TAB.equals(spec.delimiter())) {
            // A tab alone: values may hold spaces (LEEF 1.0's devTime=Oct 11 2026 10:12:12).
            return "\\t";
        }
        if (spacedDelimiter(spec)) {
            return ToolText.escape(ToolText.strip(spec.delimiter()));
        }
        return ToolText.isBlank(spec.delimiter())
                ? WHITE_SPACE
                : ToolText.escape(spec.delimiter());
    }

    /// @param spec A key_value spec.
    /// @return Whether its delimiter has white space round other characters (", ", "; "): then keys may hold
    /// spaces (Cisco ISE's `Device IP Address=`) and values run to the next delimiter.
    public static boolean spacedDelimiter(final SplitterSpec spec) {
        final String core = ToolText.strip(spec.delimiter());
        return !core.isEmpty() && !core.equals(spec.delimiter());
    }

    // What may come before a pair or a run of words: delimiters, and with a spaced delimiter white space too (after
    // a run of words, which ends at white space).
    private static String lead(final SplitterSpec spec) {
        return spacedDelimiter(spec)
                ? "(?:" + delimiter(spec) + "|\\s)*"
                : delimiter(spec) + "*";
    }

    /// @param spec A key_value spec.
    /// @return A trailing delimiter, as a record of spaced-delimited pairs may end (`..., UserName=alice,`).
    public static String trailingDelimiter(final SplitterSpec spec) {
        return delimiter(spec) + "+\\s*$";
    }

    // A key: with a spaced delimiter, words, dots and dashes, holding spaces only straight after the delimiter
    // (`, Device IP Address=`; at the start or after other words one word, so neither a queue id, `4F2A1C3D9E:
    // from=`, nor Cisco ISE's `CISE_Passed_Authentications 0001234501 3 2 AuthenticationMethod=` is a key); else
    // anything but the separator and the delimiter.
    private static String keyClass(final SplitterSpec spec) {
        if (!spacedDelimiter(spec)) {
            return "[^" + ToolText.escape(spec.pairSeparator()) + delimiterIn(spec) + "]+";
        }
        final String after = ToolText.escape(ToolText.strip(spec.delimiter())) + "\\s{0,9}";
        return "(?:(?<=" + after + ")\\w[\\w .-]*|(?<!" + after + ")\\w[\\w.-]*)";
    }

    /// The delimiter between key=value pairs: ", ", "; " or "|" where most pairs after a record's first follow one,
    /// else a space.
    ///
    /// @param records Records of pairs.
    /// @return The delimiter.
    public static String pairDelimiter(final List<String> records) {
        return pairDelimiter(records, "=");
    }

    /// @param records   Records of pairs.
    /// @param separator What separates a key from its value.
    /// @return The delimiter between the pairs, as [#pairDelimiter(List)] finds it.
    public static String pairDelimiter(final List<String> records, final String separator) {
        // A key as SampleProfiler.KEY_VALUE reads one: from no word character before it, taken whole, so a long run of
        // word characters isn't scanned again from every position in it.
        final Pattern pair = Pattern.compile("(?<!\\w)\\w[\\w.-]*+" + Pattern.quote(separator));
        long later = 0;
        for (final String record : records) {
            later += Math.max(0, pair.matcher(record).results().count() - 1);
        }
        for (final String delimiter : List.of(", ", "; ", "|")) {
            final Pattern after = Pattern.compile("\\s*" + Pattern.quote(ToolText.strip(delimiter))
                                                  + "\\s*\\w[\\w .-]*" + Pattern.quote(separator));
            long count = 0;
            for (final String record : records) {
                count += after.matcher(record).results().count();
            }
            if (later > 0 && count >= 0.8 * later) {
                return delimiter;
            }
        }
        return " ";
    }

    /// What separates keys from values: `:` where most records hold three or more quoted `key:"value"` pairs (Check
    /// Point's `action:"Accept"; ifdir:"inbound"`) and more of them than `key=value` pairs; else `=`.
    ///
    /// @param records Records.
    /// @return The separator.
    public static String pairSeparator(final List<String> records) {
        final long colons = records.stream()
                .filter(record -> SampleProfiler.COLON_PAIR.matcher(record).results().count() >= 3)
                .count();
        final long equals = records.stream()
                .filter(record -> ToolText.findallGroups(SampleProfiler.KEY_VALUE, record).size() >= 3)
                .count();
        return !records.isEmpty() && colons >= 0.8 * records.size() && colons > equals
                ? ":"
                : "=";
    }

    /// The regexes for a single-quoted key=value value (single_quotes), tried before the pair's own at each place:
    /// the first a value made of pairs (auditd's `msg='op=login res=success'`, group 2 read as pairs), the second any
    /// other (group 2 the value without its quotes). Matched from the place they are tried at: the converter anchors
    /// them (`^`), as Stroom takes a sibling's match anywhere in the text, with an error.
    ///
    /// @param spec A key_value spec.
    /// @return The two regexes.
    public static List<String> singleQuotedPatterns(final SplitterSpec spec) {
        final String s = ToolText.escape(spec.pairSeparator());
        final String key = lead(spec) + "(" + (spacedDelimiter(spec)
                ? keyClass(spec)
                : "[^" + s + delimiterIn(spec) + "'\"]+") + ")" + s + "'";
        return List.of(key + "([^']*" + s + "[^']*)'", key + "([^']*)'");
    }

    /// The regex for a run of words that aren't pairs (words): tokens holding neither the pair separator, the
    /// delimiter nor the quote, up to the next pair or the end. Group 1 is the run. Tried after the pair's own.
    ///
    /// @param spec A key_value spec.
    /// @return The regex.
    public static String wordsPattern(final SplitterSpec spec) {
        final String d = delimiterIn(spec);
        final String s = ToolText.escape(spec.pairSeparator());
        final String q = spec.quote() == null
                ? ""
                : ToolText.escape(spec.quote());
        if (spacedDelimiter(spec)) {
            // Words hold no white space; the run ends before a delimiter or a space and a key.
            final String token = "[^" + s + d + q + "\\s]+";
            final String between = "(?:" + delimiter(spec) + "|\\s+)+";
            return lead(spec) + "(" + token + "(?:" + between + token + ")*?)(?=" + between + keyClass(spec)
                   + s + "|" + delimiter(spec) + "*\\s*$)";
        }
        final String token = "[^" + s + d + q + "]+";
        final String between = delimiter(spec) + "+";
        return delimiter(spec) + "*(" + token + "(?:" + between + token + ")*?)(?=" + between + "[^" + s + d + "]+"
               + s + "|" + delimiter(spec) + "*$)";
    }

    /// The regex for one key=value pair whose value may hold the delimiter unquoted, matched along the record
    /// read backwards (Data Splitter's `reverse`): the value (group 1, backwards) runs to the separator, the key
    /// (group 2) to the delimiter, which a key never holds; white space before the value (the record's new line,
    /// read backwards) is passed over.
    ///
    /// @param spec A key_value spec.
    /// @return The regex.
    public static String spacedPairPattern(final SplitterSpec spec) {
        final String d = ToolText.isBlank(spec.delimiter())
                ? "\\s"
                : ToolText.escape(spec.delimiter());
        final String s = ToolText.escape(spec.pairSeparator());
        return "\\s*([^" + s + "]+)" + s + "([^" + d + "]+)(?:" + d + ")*";
    }

    /// Splits a line at a delimiter, leaving it inside quotes alone; a doubled quote inside a quoted value stays
    /// doubled, as Data Splitter leaves it (seen in Stroom: `"Said ""bye"""` is `Said ""bye""`).
    ///
    /// @param line      The line.
    /// @param delimiter The delimiter.
    /// @param quote     The quote character, or null.
    /// @return The cells, without their quotes.
    public static List<String> splitQuoted(final String line, final String delimiter, final String quote) {
        return splitQuoted(line, delimiter, quote, null);
    }

    /// Splits a line at a delimiter as [#splitQuoted(String, String, String)] does, with an escape character: the
    /// character after it is taken as it is (never a delimiter or quote), and the escape taken out, as Data
    /// Splitter's `$2` gives a cell.
    ///
    /// @param line      The line.
    /// @param delimiter The delimiter.
    /// @param quote     The quote character, or null.
    /// @param escape    The escape character, or null.
    /// @return The cells, without their quotes or escapes.
    public static List<String> splitQuoted(final String line,
                                           final String delimiter,
                                           final String quote,
                                           final String escape) {
        final boolean escapes = escape != null && !escape.isEmpty() && line.contains(escape);
        if (!escapes && (quote == null || quote.isEmpty() || !line.contains(quote))) {
            return ToolText.split(line, delimiter);
        }
        final List<String> out = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean inside = false;
        int i = 0;
        while (i < line.length()) {
            if (escapes && line.startsWith(escape, i) && i + escape.length() < line.length()) {
                i += escape.length();
                cell.append(line.charAt(i));
                i++;
                continue;
            }
            if (quote == null || quote.isEmpty()) {
                if (line.startsWith(delimiter, i)) {
                    out.add(cell.toString());
                    cell.setLength(0);
                    i += delimiter.length();
                } else {
                    cell.append(line.charAt(i));
                    i++;
                }
                continue;
            }
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
        if (spec.recordKey() != null) {
            return KeyedRecords.apply(spec, text);
        }
        if (spec.continuation()) {
            return withContinuation(spec, text, heading);
        }
        if (SplitterSpec.ALTERNATIVES.equals(spec.kind())) {
            return applyAlternatives(spec, text);
        }
        SplitterSpec applied = spec;
        if ("syslog".equals(spec.kind())) {
            final boolean rfc5424 = "rfc5424".equals(spec.rfc());
            applied = SplitterSpec.builder("regex")
                    .pattern(rfc5424
                            ? SYSLOG_5424
                            : SYSLOG_3164)
                    .names(spec.syslogNames())
                    .body(spec.body())
                    .bodyField(spec.bodyField())
                    .build();
        }
        if ("cef".equals(applied.kind())) {
            return applyCef(applied, text);
        }
        if ("fields".equals(applied.kind())) {
            return applyFields(applied, text);
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
                    // Trimmed, as Stroom trims a data element's value (seen: a message's trailing spaces).
                    fields.put(name, m.group(n) == null
                            ? ""
                            : ToolText.strip(m.group(n)));
                }
            }
        } else if ("key_value".equals(applied.kind())) {
            fields = applyKeyValue(applied, text);
            if (fields.isEmpty()) {
                return null;
            }
        } else if (applied.headerLine()) {
            // As Stroom's split gives a cell ($1, or $2 with escapes): without the white space round it, quoted or
            // not; a cell past the heading is written with no name (seen in Stroom).
            final List<String> cells = splitQuoted(text, applied.delimiter(), applied.quote(), applied.escape());
            fields = new LinkedHashMap<>();
            for (int n = 0; n < cells.size(); n++) {
                if (heading != null) {
                    fields.putIfAbsent(n < heading.size()
                            ? heading.get(n)
                            : StroomSplitterRun.UNNAMED, ToolText.strip(cells.get(n)));
                }
            }
        } else if (applied.escape() != null) {
            // Named columns with escapes: a split per column, each taking one cell in turn.
            final List<String> cells = splitQuoted(text, applied.delimiter(), applied.quote(), applied.escape());
            fields = new LinkedHashMap<>();
            for (int n = 0; n < Math.min(applied.columns().size(), cells.size()); n++) {
                fields.put(applied.columns().get(n), ToolText.strip(cells.get(n)));
            }
            if (cells.size() > applied.columns().size()) {
                // Stroom reports the cells past the last column as content no expression matched.
                return null;
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
            if ((inner == null || inner.isEmpty()) && bodyText != null && !ToolText.isBlank(bodyText)) {
                // Stroom reports a body its expressions don't match ("Expressions failed to match all of the
                // content"): so does the dry run, rather than keeping the record without the body's fields (seen: a
                // Palo Alto body short of a column, fine here and refused in Stroom).
                return null;
            }
            if (inner != null && !inner.isEmpty()) {
                fields = new LinkedHashMap<>(fields);
                // A body's field named as one before it (Check Point's time:"..." after the syslog header's time) is
                // a second data element of that name: a field reads the first, as Stroom's records show it.
                inner.forEach(fields::putIfAbsent);
            }
        }
        return fields;
    }

    // A record's first line read by the spec, the lines after it kept whole, as the converter's continuation
    // regex divides them.
    private static Map<String, String> withContinuation(final SplitterSpec spec,
                                                        final String text,
                                                        final List<String> heading) {
        final int newline = text.indexOf('\n');
        final SplitterSpec firstLine = spec.toBuilder().continuation(false).recordStart(null).catchAll(false).build();
        final String first = newline < 0
                ? text
                : text.substring(0, newline);
        Map<String, String> fields = applyRecord(firstLine, first, heading);
        if (fields == null && spec.catchAll() && !claimed(firstLine, first) && !ToolText.isBlank(first)) {
            // The converter's catch-all, inside the first line's group: the first line kept whole (trimmed, as
            // Stroom trims a data element's value).
            fields = Map.of(SplitterSpec.UNPARSED, ToolText.strip(first));
        }
        if (fields == null) {
            return null;
        }
        final Map<String, String> out = new LinkedHashMap<>(fields);
        // Trimmed as Stroom trims a data element's value (seen: a cut stack trace's first frame without its tab); a
        // record of one line has an empty one, as the converter's optional group writes it.
        out.put(SplitterSpec.CONTINUATION, newline < 0
                ? ""
                : ToolText.strip(text.substring(newline + 1)));
        return out;
    }

    /// Whether a record is an alternatives part's to read (its match fits the record's start), so that a record the
    /// part can't read is an error, not kept by catch_all, as Stroom reports it.
    ///
    /// @param spec A spec.
    /// @param text A record.
    /// @return True for an alternatives spec one of whose parts' match fits; false otherwise.
    static boolean claimed(final SplitterSpec spec, final String text) {
        if (!SplitterSpec.ALTERNATIVES.equals(spec.kind())) {
            return false;
        }
        final String first = spec.continuation() && text.indexOf('\n') >= 0
                ? text.substring(0, text.indexOf('\n'))
                : text;
        return spec.parts().stream()
                .anyMatch(part -> SafePattern.matcher(SafePattern.compile(part.guard(), 0), first).lookingAt());
    }

    // The first part whose match fits the record's start reads it, its variant written first; a record that part
    // can't read is not given to the next (Stroom reports it), and one no part's match fits is unmatched.
    private static Map<String, String> applyAlternatives(final SplitterSpec spec, final String text) {
        for (final SplitterSpec part : spec.parts()) {
            if (SafePattern.matcher(SafePattern.compile(part.guard(), 0), text).lookingAt()) {
                final Map<String, String> read = applyRecord(part, text, null);
                if (read == null) {
                    return null;
                }
                final Map<String, String> out = new LinkedHashMap<>();
                out.put(spec.variantField(), part.variant());
                out.putAll(read);
                return out;
            }
        }
        return null;
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
        if (spec.body() != null && !ToolText.isBlank(m.group(1))) {
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
        if (spec.spacedValues()) {
            return applySpacedPairs(spec, text);
        }
        final List<Pattern> patterns = new ArrayList<>();
        kvPatterns(spec).forEach(p -> patterns.add(Pattern.compile(p)));
        final List<Pattern> quoted = spec.singleQuotes()
                ? singleQuotedPatterns(spec).stream().map(Pattern::compile).toList()
                : List.of();
        final Pattern words = spec.words()
                ? Pattern.compile(wordsPattern(spec))
                : null;
        final Map<String, String> fields = new LinkedHashMap<>();
        int at = 0;
        while (at < text.length()) {
            final Matcher pairs = quoted.isEmpty()
                    ? null
                    : ToolText.matchFrom(quoted.getFirst(), new DeadlineCharSequence(text), at);
            if (pairs != null) {
                // A single-quoted value of pairs: the record's own pairs, after those before it.
                applyKeyValue(spec, pairs.group(2)).forEach(fields::putIfAbsent);
                at = pairs.end();
                continue;
            }
            final Matcher single = quoted.isEmpty()
                    ? null
                    : ToolText.matchFrom(quoted.getLast(), new DeadlineCharSequence(text), at);
            if (single != null) {
                fields.putIfAbsent(single.group(1), ToolText.strip(single.group(2)));
                at = single.end();
                continue;
            }
            Matcher found = null;
            for (final Pattern pattern : patterns) {
                final Matcher m = ToolText.matchFrom(pattern, new DeadlineCharSequence(text), at);
                if (m != null && m.end() > at) {
                    found = m;
                    break;
                }
            }
            if (found == null && words != null) {
                final Matcher run = ToolText.matchFrom(words, new DeadlineCharSequence(text), at);
                if (run != null && run.end() > at) {
                    // Text that isn't pairs, kept: the first run, as a field reads the first of several.
                    fields.putIfAbsent(SplitterSpec.WORDS, ToolText.strip(run.group(1)));
                    at = run.end();
                    continue;
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
        final String rest = text.substring(at);
        if (!ToolText.isBlank(rest) && !(spacedDelimiter(spec)
                                        && Pattern.compile(trailingDelimiter(spec)).matcher(rest).lookingAt())) {
            // Stroom reports the rest as content its expressions failed to match (seen: a value holding a space
            // unquoted, `zones=Zone 1`, the record kept with zones=Zone and the error raised).
            return Map.of();
        }
        return fields;
    }

    // Pairs read from the end, as the converter's reversed group reads them: Stroom writes them last first, so
    // the first a key is given in that order is the one kept.
    private static Map<String, String> applySpacedPairs(final SplitterSpec spec, final String text) {
        final String reversed = new StringBuilder(text).reverse().toString();
        final Pattern pair = SafePattern.compile(spacedPairPattern(spec), 0);
        final Map<String, String> fields = new LinkedHashMap<>();
        int at = 0;
        while (at < reversed.length()) {
            final Matcher m = ToolText.matchFrom(pair, new DeadlineCharSequence(reversed), at);
            if (m == null || m.end() <= at) {
                break;
            }
            fields.putIfAbsent(new StringBuilder(m.group(2)).reverse().toString(),
                    new StringBuilder(m.group(1)).reverse().toString());
            at = m.end();
        }
        return ToolText.isBlank(reversed.substring(at))
                ? fields
                : Map.of();
    }

    // Each field found by its own regex anywhere in the record, in the order given, each match taken out of the
    // text (Data Splitter's matchOrder any); with catch_all, what no field took is kept as `unparsed`. A record
    // with none of them writes nothing (no record, and no error in Stroom).
    private static Map<String, String> applyFields(final SplitterSpec spec, final String text) {
        String rest = text;
        final Map<String, String> fields = new LinkedHashMap<>();
        for (final Map.Entry<String, String> field : spec.fields().entrySet()) {
            final Pattern pattern = SafePattern.compile(field.getValue(), 0);
            Matcher m = SafePattern.matcher(pattern, rest);
            while (m.find() && m.end() > m.start()) {
                fields.putIfAbsent(field.getKey(), m.groupCount() > 0
                        ? NullSafe.string(m.group(1))
                        : m.group());
                rest = rest.substring(0, m.start()) + rest.substring(m.end());
                m = SafePattern.matcher(pattern, rest);
            }
        }
        if (spec.catchAll() && !ToolText.isBlank(rest)) {
            fields.put(SplitterSpec.UNPARSED, ToolText.strip(rest));
        }
        return fields.isEmpty()
                ? null
                : fields;
    }

    /// @param messages Syslog messages' bodies.
    /// @return How they divide further, when they do: key=value pairs, or delimited columns (as Palo Alto firewalls
    /// send them: a syslog header, then their CSV), named by position for the model to rename; else null.
    static SplitterSpec bodySpec(final List<String> messages) {
        final List<String> bodies = new ArrayList<>();
        for (final String message : messages) {
            if (!ToolText.isBlank(message)) {
                bodies.add(message);
            }
        }
        if (":".equals(pairSeparator(bodies))) {
            // key:"value" pairs (Check Point's).
            return withWords(SplitterSpec.builder("key_value").pairSeparator(":").quote("\"")
                    .delimiter(pairDelimiter(bodies, ":")).build(), bodies);
        }
        int pairs = 0;
        for (final String body : bodies) {
            if (ToolText.findallGroups(SampleProfiler.KEY_VALUE, body).size() >= 2) {
                pairs++;
            }
        }
        if (!bodies.isEmpty() && pairs >= 0.8 * bodies.size()) {
            final boolean quoted = messages.stream().anyMatch(message -> message.contains("=\""));
            final String delimiter = pairDelimiter(bodies);
            return withWords(SplitterSpec.builder("key_value").delimiter(delimiter).quote(quoted
                    ? "\""
                    : null).spacedValues(!quoted && " ".equals(delimiter) && spacedValues(bodies, " ", "="))
                    .build(), bodies);
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

    /// A key=value spec that keeps the text between its pairs (words) when some records hold text that isn't pairs
    /// (a prefix, bare flags, verbs) and are read whole with it; else the spec as it is.
    ///
    /// @param spec    A key_value spec.
    /// @param records The records.
    /// @return The spec, with words where they read records it can't.
    static SplitterSpec wordsWhereNeeded(final SplitterSpec spec, final List<String> records) {
        if (spec.spacedValues() || spec.words()) {
            return spec;
        }
        final SplitterSpec worded = spec.toBuilder().words(true).build();
        return records.stream().limit(200)
                .anyMatch(record -> applyKeyValue(spec, record).isEmpty() && !applyKeyValue(worded, record).isEmpty())
                ? worded
                : spec;
    }

    static SplitterSpec withWords(final SplitterSpec spec, final List<String> records) {
        if (spec.spacedValues() || spec.words()) {
            return spec;
        }
        final SplitterSpec prefixed = withPrefix(spec, records);
        if (prefixed != null) {
            return prefixed;
        }
        return wordsWhereNeeded(spec, records);
    }

    // Records of the same few words before their pairs and nothing else (08's `2026-09-28T15:00:00Z fw01
    // action=allow ...`): the words fields of their own (a time named time, others prefix1, prefix2...), the pairs
    // read by the spec as the body; null unless every record is that.
    private static SplitterSpec withPrefix(final SplitterSpec spec, final List<String> records) {
        if (spec.recordKey() != null) {
            // Lines tied into records: their spec reads each line, not a prefix.
            return null;
        }
        final List<String> sampled = records.stream().filter(record -> !ToolText.isBlank(record)).limit(200).toList();
        int count = -1;
        final List<List<String>> prefixes = new ArrayList<>();
        for (final String record : sampled) {
            final Matcher prefix = PREFIX_WORDS.matcher(ToolText.strip(record));
            if (!prefix.lookingAt() || applyKeyValue(spec, prefix.group(2)).isEmpty()) {
                return null;
            }
            final List<String> words = List.of(ToolText.strip(prefix.group(1)).split("\\s+"));
            if (count >= 0 && words.size() != count) {
                return null;
            }
            count = words.size();
            prefixes.add(words);
        }
        if (count < 1 || count > MOST_PREFIX_WORDS) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            final int at = n;
            final boolean times = prefixes.stream()
                    .allMatch(words -> TimePatterns.inferTimePattern(words.get(at)) != null);
            names.add(times && !names.contains("time")
                    ? "time"
                    : "prefix" + (n + 1));
        }
        names.add("message");
        return SplitterSpec.builder("regex").pattern("^" + "(\\S+)\\s+".repeat(count) + "(.*)$").names(names)
                .body(spec).bodyField("message").build();
    }

    /// Whether key=value text has values holding the delimiter unquoted (`zones=Zone 1, Zone 2 user=An end user`):
    /// some record's pairs, read as ending at the delimiter, leave text no pair matches, which Stroom reports and
    /// loses (seen: the Stroom docs' reverse example), and every such record reads whole from the end. Text neither
    /// reading parses (sudo's `alice : TTY=pts/0 ; ...`) keeps the plain reading.
    ///
    /// @param lines         The records.
    /// @param delimiter     What separates pairs.
    /// @param pairSeparator What separates a key from its value.
    /// @return True if reading from the end is what parses them.
    static boolean spacedValues(final List<String> lines, final String delimiter, final String pairSeparator) {
        final SplitterSpec plain = SplitterSpec.builder("key_value").delimiter(delimiter).pairSeparator(pairSeparator)
                .build();
        final SplitterSpec spaced = plain.toBuilder().spacedValues(true).build();
        boolean needed = false;
        for (final String line : lines.subList(0, Math.min(200, lines.size()))) {
            if (ToolText.isBlank(line) || !applyKeyValue(plain, line).isEmpty()) {
                continue;
            }
            if (applyKeyValue(spaced, line).isEmpty()) {
                return false;
            }
            needed = true;
        }
        return needed;
    }

    /// Infers a spec for a sample's format, from its profile.
    ///
    /// @param sample The sample.
    /// @return The spec (null when the format needs no text converter, JSON or XML, or can't be inferred: free
    /// text, which needs a regex from a human) and the sample's profile.
    public static Inferred inferSpec(final String sample) {
        final Map<String, Object> info = SampleProfiler.profile(sample);
        // Read as Stroom's parser reads it: the characters XML doesn't allow taken out first.
        final Inferred inferred = inferFormat(StroomInput.asParsed(sample), info);
        if (inferred.spec() == null || !Boolean.TRUE.equals(info.get(SplitterSpec.CONTINUATION))) {
            return inferred;
        }
        // Records over several lines (an entry and its stack trace): the first line read by the format, the rest
        // kept as the continuation.
        return new Inferred(inferred.spec().toBuilder().recordStart(LineFormats.RECORD_LINE_START).continuation(true)
                .build(), info);
    }

    private static Inferred inferFormat(final String sample, final Map<String, Object> info) {
        final String format = (String) info.get("format");
        final List<String> all = ToolText.nonBlankLines(sample);
        final LineFormats.Grouped grouped = Boolean.TRUE.equals(info.get(SplitterSpec.CONTINUATION))
                ? LineFormats.group(all)
                : null;
        final List<String> records = grouped == null
                ? all
                : grouped.records();
        // Each record's first line is what the format reads.
        final List<String> lines = LineFormats.firstLines(records);
        if (SampleProfiler.KEYED_SECTIONS.equals(format)) {
            // Each section kept whole under its marker's name, the marker line itself left out.
            return new Inferred(SplitterSpec.builder("regex").pattern(SECTION_TEXT).names(List.of(SECTION_FIELD))
                    .recordKey((String) info.get("record_key")).keyField((String) info.get("key_field"))
                    .lineName((String) info.get("line_name")).leadField((String) info.get("lead_field"))
                    .joinUnkeyed(true).build(), info);
        }
        if (SampleProfiler.MIXED_LINES.equals(format)) {
            return new Inferred(mixedSpec(records, info.get("unrecognised") != null), info);
        }
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
            final List<String> head = lines.subList(0, Math.min(200, lines.size()));
            final String separator = pairSeparator(head);
            final String delimiter = pairDelimiter(head, separator);
            final SplitterSpec.Builder builder = SplitterSpec.builder("key_value").delimiter(delimiter)
                    .pairSeparator(separator)
                    .quote(quoted || ":".equals(separator)
                            ? "\""
                            : null)
                    .spacedValues(!quoted && "=".equals(separator) && " ".equals(delimiter)
                                  && spacedValues(lines, " ", "="));
            if (info.get("record_key") instanceof final String key) {
                // Lines tied into records by a key (auditd): each line's fields under its name, the lines between
                // records (ausearch's separators) passed over.
                builder.recordKey(key).keyField((String) info.get("key_field"))
                        .lineName((String) info.get("line_name")).leadField((String) info.get("lead_field"))
                        .skipLines((String) info.get("skip_lines"));
            }
            // Values in single quotes as well (auditd's msg='op=login ... res=success'): read without them.
            builder.singleQuotes(lines.subList(0, Math.min(50, lines.size())).stream()
                    .anyMatch(line -> line.contains("='")));
            return new Inferred(withWords(builder.build(), lines), info);
        }
        if (SampleProfiler.CEF_FORMAT.equals(format)) {
            return new Inferred(cefSpec(lines), info);
        }
        if (SampleProfiler.LEEF_FORMAT.equals(format)) {
            return new Inferred(leefSpec(lines), info);
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
            if (info.get(SampleProfiler.PREFIX_WORDS) instanceof final Integer words && words > 0) {
                return new Inferred(prefixedSyslog(rfc, words, lines), info);
            }
            final List<SplitterSpec> parts = syslogParts(rfc, lines, "syslog");
            return new Inferred(parts.size() == 1
                    ? parts.getFirst().toBuilder().variant(null).build()
                    : SplitterSpec.builder(SplitterSpec.ALTERNATIVES).parts(parts).build(), info);
        }
        return new Inferred(null, info);
    }

    // A syslog spec for lines of its header, its messages' body read as they are: key=value, delimited, or (some
    // key=value, some not) each by its own part.
    private static SplitterSpec.Builder syslogSpec(final String rfc, final List<String> lines) {
        return syslogSpec(rfc, lines, true);
    }

    // As above; with nested, a message that is itself a syslog line (a relay's header before the source's own) read
    // as that header, the relay's fields named relay_time, relay_host... One level only: a relay's relay is left in
    // the message.
    private static SplitterSpec.Builder syslogSpec(final String rfc, final List<String> lines, final boolean nested) {
        final SplitterSpec head = SplitterSpec.builder("syslog").rfc(rfc).build();
        final List<String> messages = new ArrayList<>();
        final List<String> structured = new ArrayList<>();
        dryRun(head, String.join("\n", lines)).records().forEach(record -> {
            messages.add(record.getOrDefault("message", ""));
            structured.add(record.getOrDefault(STRUCTURED, ""));
        });
        final String innerRfc = nested
                ? nestedRfc(messages)
                : null;
        if (innerRfc != null) {
            final List<String> inner = messages.stream().filter(message -> !ToolText.isBlank(message)).toList();
            return SplitterSpec.builder("syslog").rfc(rfc).namePrefix(SyslogHeaders.RELAY_PREFIX)
                    .body(syslogSpec(innerRfc, inner, false).build());
        }
        final SplitterSpec inBrackets = structuredBody(messages, structured);
        if (inBrackets != null) {
            return SplitterSpec.builder("syslog").rfc(rfc).body(inBrackets).bodyField(STRUCTURED);
        }
        SplitterSpec body = bodySpec(messages);
        if (body == null) {
            body = mixedBody(messages);
        }
        return SplitterSpec.builder("syslog").rfc(rfc).body(body);
    }

    // Syslog lines, some of them relayed (a relay's header before the source's own) and some not, in one stream (a
    // relay forwarding some sources while others send directly): one regex whose relay header may be there or not, so
    // a kind of event is one kind however its line came (seen as two parts: a login relayed and a login sent directly
    // were two rules, sharing a TypeId, which the mapping's check refused, and two questions to the user). The relayed
    // sources in another header format than the direct ones can't share a regex: a part each, the relayed first. One
    // spec when the lines are all of one kind (four in five relayed are read as relayed).
    private static List<SplitterSpec> syslogParts(final String rfc, final List<String> lines, final String variant) {
        final List<String> relayed = new ArrayList<>();
        final List<String> direct = new ArrayList<>();
        for (final String line : lines) {
            final String message = SyslogHeaders.messageOf(line);
            if (message != null && SyslogHeaders.startsWithHeader(message)) {
                relayed.add(line);
            } else {
                direct.add(line);
            }
        }
        if (relayed.isEmpty() || direct.isEmpty() || relayed.size() >= 0.8 * lines.size()) {
            return List.of(syslogSpec(rfc, lines).variant(variant).build());
        }
        final SplitterSpec relayedPart = syslogSpec(rfc, relayed).variant(variant + "_relayed")
                .match(SyslogHeaders.relayedStart(rfc)).build();
        if (relayedPart.namePrefix() == null) {
            // The relayed lines' sources are in no one header format: read as the others are.
            return List.of(syslogSpec(rfc, lines, false).variant(variant).build());
        }
        if (rfc.equals(relayedPart.body().rfc())) {
            return List.of(optionalRelaySpec(rfc, relayed, direct, variant));
        }
        return List.of(relayedPart, syslogSpec(rfc, direct, false).variant(variant).build());
    }

    // One regex for lines relayed and sent directly, sources all in the relay's header format: the relay's fields
    // empty on a line sent directly, the source's under the plain names, its message's body read as the spec for all
    // the sources' lines reads it.
    private static SplitterSpec optionalRelaySpec(final String rfc,
                                                  final List<String> relayed,
                                                  final List<String> direct,
                                                  final String variant) {
        final List<String> sources = new ArrayList<>(direct);
        relayed.forEach(line -> sources.add(SyslogHeaders.messageOf(line)));
        final SplitterSpec source = syslogSpec(rfc, sources, false).build();
        final List<String> names = new ArrayList<>(SyslogHeaders.relayNames(rfc));
        names.addAll(source.syslogNames());
        return SplitterSpec.builder("regex")
                .pattern("^" + SyslogHeaders.optionalRelay(rfc) + ("rfc5424".equals(rfc)
                        ? SYSLOG_5424
                        : SYSLOG_3164).substring(1))
                .names(names)
                .body(source.body())
                .bodyField(source.bodyField())
                .variant(variant)
                .build();
    }

    // The header format most messages start with, when four in five do (syslog within syslog: a relay that sent the
    // source's line on as its message); else null.
    private static String nestedRfc(final List<String> messages) {
        final List<String> bodies = messages.stream().filter(message -> !ToolText.isBlank(message)).toList();
        final Map<String, Integer> formats = new LinkedHashMap<>();
        bodies.forEach(body -> {
            final String format = SyslogHeaders.formatOf(body);
            if (format != null) {
                formats.merge(format, 1, Integer::sum);
            }
        });
        return formats.entrySet().stream()
                .filter(entry -> entry.getValue() >= 0.8 * bodies.size())
                .map(entry -> SampleProfiler.SYSLOG_5424_FORMAT.equals(entry.getKey())
                        ? "rfc5424"
                        : "rfc3164")
                .findFirst()
                .orElse(null);
    }

    // Lines of a collector's words then a syslog header (`2026-11-09T10:00:00Z relay01 <14>Oct ...`): the words read
    // as fields of the relay's (a time as relay_time, the others relay_word1, relay_word2...), the rest as the syslog
    // line it is.
    private static SplitterSpec prefixedSyslog(final String rfc, final int words, final List<String> lines) {
        final List<String> stripped = lines.stream().map(line -> SyslogHeaders.withoutWords(line, words)).toList();
        final List<List<String>> prefixes = lines.stream()
                .map(line -> List.of(ToolText.strip(line).split("\\s+")).subList(0, words))
                .filter(prefix -> prefix.size() == words)
                .toList();
        final List<String> names = new ArrayList<>();
        final String time = SyslogHeaders.RELAY_PREFIX + "time";
        for (int n = 0; n < words; n++) {
            final int at = n;
            final boolean times = !prefixes.isEmpty() && prefixes.stream()
                    .allMatch(prefix -> TimePatterns.inferTimePattern(prefix.get(at)) != null);
            names.add(times && !names.contains(time)
                    ? time
                    : SyslogHeaders.RELAY_PREFIX + "word" + (n + 1));
        }
        final String line = SyslogHeaders.RELAY_PREFIX + "line";
        names.add(line);
        return SplitterSpec.builder("regex").pattern("^" + "(\\S+)\\s+".repeat(words) + "(.*)$").names(names)
                .body(syslogSpec(rfc, stripped).build()).bodyField(line).build();
    }

    // Where most messages are empty and the structured data in brackets holds pairs (Check Point's Log Exporter:
    // `[action:"Accept"; ifdir:"inbound"; ...]`): the brackets' text read by the pairs' spec; else null.
    private static SplitterSpec structuredBody(final List<String> messages, final List<String> structured) {
        final long empty = messages.stream().filter(ToolText::isBlank).count();
        final List<String> inside = structured.stream()
                .map(text -> ToolText.strip(text))
                .filter(text -> text.startsWith("[") && text.endsWith("]"))
                .map(text -> text.substring(1, text.length() - 1))
                .toList();
        if (messages.isEmpty() || empty < 0.8 * messages.size() || inside.size() < 0.8 * structured.size()) {
            return null;
        }
        final SplitterSpec pairs = bodySpec(inside);
        if (pairs == null || !"key_value".equals(pairs.kind())) {
            return null;
        }
        return SplitterSpec.builder("regex").pattern(BRACKETED).names(List.of("structured_data")).body(pairs)
                .bodyField("structured_data").build();
    }

    // Messages some of which are key=value pairs (two or more) and some not, each a share of them: a key=value part,
    // then the rest kept as text, their variant in message_format.
    private static SplitterSpec mixedBody(final List<String> messages) {
        final List<String> bodies = messages.stream().filter(message -> !ToolText.isBlank(message)).toList();
        final long pairs = bodies.stream()
                .filter(body -> ToolText.findallGroups(SampleProfiler.KEY_VALUE, body).size() >= 2)
                .count();
        if (bodies.isEmpty() || pairs < LineFormats.MIXED_SHARE * bodies.size()
            || bodies.size() - pairs < LineFormats.MIXED_SHARE * bodies.size()) {
            return null;
        }
        final boolean quoted = bodies.stream().anyMatch(body -> body.contains("=\""));
        final List<String> paired = bodies.stream()
                .filter(body -> ToolText.findallGroups(SampleProfiler.KEY_VALUE, body).size() >= 2)
                .toList();
        final SplitterSpec pairPart = SplitterSpec.builder("key_value").variant("key_value")
                .delimiter(pairDelimiter(paired)).quote(quoted
                        ? "\""
                        : null).build();
        return SplitterSpec.builder(SplitterSpec.ALTERNATIVES).variantField("message_format").parts(List.of(
                wordsWhereNeeded(pairPart, paired),
                SplitterSpec.builder("regex").variant("text").pattern("(?s)^(.*)$").names(List.of("text"))
                        .build())).build();
    }

    // Records in several formats: a part per format, in the order a record is best told apart (CEF before syslog,
    // syslog before key=value, timestamped text last), each read as the format's own spec reads it; catch_all where
    // some records are in none of them.
    private static SplitterSpec mixedSpec(final List<String> records, final boolean unrecognised) {
        final LineFormats.Classified classified = LineFormats.classify(records);
        final List<SplitterSpec> parts = new ArrayList<>();
        // Every format the records hold has its part, those under the share that made the stream mixed too (seen: a
        // twentieth of a stream in CEF, kept unparsed). A date header's part comes last, as it reads any line holding
        // a date.
        classified.byFormat().forEach((format, held) -> {
            final List<String> lines = LineFormats.firstLines(held);
            if (LineFormats.TIMESTAMPED_TEXT.equals(format)) {
                parts.addAll(textParts(lines));
            } else if (SampleProfiler.SYSLOG_5424_FORMAT.equals(format)) {
                parts.addAll(syslogParts("rfc5424", lines, "syslog_5424"));
            } else if (SampleProfiler.SYSLOG_3164_FORMAT.equals(format)) {
                parts.addAll(syslogParts("rfc3164", lines, "syslog"));
            } else if (LineFormats.DATE_HEADER.equals(format)) {
                parts.add(headerPart(lines));
            } else {
                parts.add(partFor(format, lines));
            }
        });
        // catch_all keeps what no part reads: records in no format, and a time of a shape past the parts'.
        return SplitterSpec.builder(SplitterSpec.ALTERNATIVES).parts(parts).catchAll(unrecognised
                || !classified.unrecognised().isEmpty() || textShapesLeft(LineFormats.firstLines(classified.byFormat()
                .getOrDefault(LineFormats.TIMESTAMPED_TEXT, List.of())))).build();
    }

    // A header line giving the date of the lines after it: the date it holds, of the layout most of them use, as the
    // field date; the rest of the line passed over.
    private static SplitterSpec headerPart(final List<String> lines) {
        final Map<String, Integer> dates = new LinkedHashMap<>();
        lines.forEach(line -> dates.merge(Objects.requireNonNullElse(LineFormats.dateIn(line), ""), 1,
                Integer::sum));
        final String date = dates.entrySet().stream()
                .filter(entry -> !entry.getKey().isEmpty())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(LineFormats.DATES.getFirst());
        return SplitterSpec.builder("regex").variant(HEADER_VARIANT).pattern("^.*?(" + date + ").*$")
                .names(List.of(HEADER_DATE)).carry(CARRIED_DATE).build();
    }

    // The part that reads lines of one format, named by it.
    private static SplitterSpec partFor(final String format, final List<String> lines) {
        return switch (format) {
            case SampleProfiler.CEF_FORMAT -> cefSpec(lines).toBuilder().variant("cef").build();
            case SampleProfiler.KEY_VALUE_FORMAT -> {
                final boolean quoted = lines.stream().anyMatch(line -> line.contains("=\""));
                yield SplitterSpec.builder("key_value").variant("key_value").delimiter(" ").quote(quoted
                        ? "\""
                        : null).spacedValues(!quoted && spacedValues(lines, " ", "=")).build();
            }
            default -> throw new IllegalArgumentException("No part reads " + format);
        };
    }

    // Timestamped text: a part per shape of its time (each shape its own time format, for the mapping: 2026-10-11
    // 22:14:00.123 and 2026-10-11T22:14:00.123+10:00 apart), the most used first and named text, the others text_2,
    // text_3...; at most MAX_TEXT_PARTS, the rest kept by catch_all. Each reads the time, and the rest as the message
    // (its own pattern is the model's to give).
    private static List<SplitterSpec> textParts(final List<String> lines) {
        final List<String> patterns = textShapes(lines).stream().limit(MAX_TEXT_PARTS).toList();
        final List<SplitterSpec> parts = new ArrayList<>();
        for (int n = 0; n < patterns.size(); n++) {
            parts.add(SplitterSpec.builder("regex").variant(n == 0
                    ? "text"
                    : "text_" + (n + 1)).pattern(patterns.get(n)).names(List.of("time", "message")).build());
        }
        return parts;
    }

    // Each shape of time timestamped text has, as the regex reading such a line (the time, then the message), the
    // most used first.
    private static List<String> textShapes(final List<String> lines) {
        final Map<String, Integer> shapes = new LinkedHashMap<>();
        for (final String line : lines) {
            final String start = LineFormats.timeStart(line);
            if (start == null) {
                continue;
            }
            final boolean bracketed = start.startsWith("\\[");
            final Matcher token = Pattern.compile(bracketed
                    ? "^\\[([^\\]]+)\\]"
                    : "^(" + start + "\\S*(?: [+-]\\d{4})?)(?=\\s)").matcher(line);
            if (token.find()) {
                shapes.merge((bracketed
                        ? "^\\[("
                        : "^(") + shapeOf(token.group(1)) + (bracketed
                        ? ")\\]"
                        : ")") + "\\s+(.*)$", 1, Integer::sum);
            }
        }
        return shapes.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    // Whether timestamped text has more shapes of time than it gets parts.
    private static boolean textShapesLeft(final List<String> lines) {
        return textShapes(lines).size() > MAX_TEXT_PARTS;
    }

    // A regex of a time's shape: each digit a digit, each letter a letter, every other character itself.
    private static String shapeOf(final String time) {
        final StringBuilder shape = new StringBuilder();
        for (final char c : time.toCharArray()) {
            if (Character.isDigit(c)) {
                shape.append("\\d");
            } else if (Character.isLetter(c)) {
                shape.append("[A-Za-z]");
            } else {
                shape.append(ToolText.escape(String.valueOf(c)));
            }
        }
        return shape.toString();
    }

    // A syslog header before CEF: the first header pattern that fits every line's text before CEF.
    // LEEF (IBM QRadar's Log Event Extended Format): `LEEF:2.0|Vendor|Product|Version|EventID|^|key=value^...`, the
    // attributes' delimiter declared in the header (2.0: a character or its hex, x5E) or a tab (1.0), after any
    // syslog header (read as CEF's prefix is). A regex reading the prefix and the header, the attributes its body.
    private static SplitterSpec leefSpec(final List<String> lines) {
        final List<String> prefixes = new ArrayList<>();
        final List<String> declared = new ArrayList<>();
        int second = 0;
        for (final String line : lines) {
            final Matcher m = LEEF_HEAD.matcher(line);
            if (m.lookingAt()) {
                prefixes.add(m.group(1));
                if (m.group(2).startsWith("2")) {
                    second++;
                    declared.add(Objects.requireNonNullElse(m.group(3), ""));
                }
            }
        }
        final boolean two = second > prefixes.size() / 2;
        final String delimiter = two
                ? leefDelimiter(declared.stream()
                .collect(Collectors.groupingBy(d -> d, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""))
                : TAB;
        final List<String> names = new ArrayList<>();
        String prefix = "";
        if (prefixes.stream().anyMatch(text -> !ToolText.isBlank(text))) {
            Map.Entry<String, List<String>> chosen = CEF_PREFIXES.getLast();
            for (final Map.Entry<String, List<String>> candidate : CEF_PREFIXES) {
                final Pattern pattern = Pattern.compile(candidate.getKey());
                if (prefixes.stream().allMatch(text -> pattern.matcher(text).lookingAt())) {
                    chosen = candidate;
                    break;
                }
            }
            // The prefix's own regex, unanchored at its end, then the header after any white space.
            prefix = chosen.getKey().substring(1, chosen.getKey().length() - "\\s*$".length()) + "\\s*";
            names.addAll(chosen.getValue());
        }
        final String field = "(" + ToolText.escapedRun("|") + ")\\|";
        names.addAll(List.of("leef_version", "leef_vendor", "leef_product", "leef_product_version", "leef_event_id"));
        if (two) {
            names.add("leef_delimiter");
        }
        names.add(LEEF_ATTRIBUTES);
        final SplitterSpec attributes = SplitterSpec.builder("key_value").delimiter(delimiter).build();
        return SplitterSpec.builder("regex")
                .pattern("^" + prefix + "LEEF:(\\d\\.\\d)\\|" + field.repeat(4) + (two
                        ? "([^|]{0,4})\\|"
                        : "") + "(.*)$")
                .names(names)
                .body(attributes)
                .bodyField(LEEF_ATTRIBUTES)
                .build();
    }

    // A LEEF 2.0 header's declared delimiter: a character, or its hex (x5E, 0x5E); a tab when none.
    private static String leefDelimiter(final String declared) {
        final Matcher hex = LEEF_HEX.matcher(declared);
        if (hex.matches()) {
            return String.valueOf((char) Integer.parseInt(hex.group(1), 16));
        }
        return declared.isEmpty()
                ? TAB
                : declared;
    }

    private static SplitterSpec cefSpec(final List<String> lines) {
        final List<String> prefixes = new ArrayList<>();
        for (final String line : lines) {
            final Matcher m = CEF.matcher(line);
            if (m.lookingAt()) {
                prefixes.add(m.group(1));
            }
        }
        SplitterSpec body = null;
        if (prefixes.stream().anyMatch(prefix -> !ToolText.isBlank(prefix))) {
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
        if (SplitterSpec.CONVERTER.equals(spec.kind())) {
            return converterRun(spec.converter(), sample, maxRecords);
        }
        // Read as Stroom's parser reads it: the characters XML doesn't allow taken out first.
        final String read = StroomInput.asParsed(sample);
        return SafePattern.limited(patterns(spec), () -> dryRunLimited(spec, read, maxRecords));
    }

    // A saved Data Splitter read by Stroom's own parser (see SplitterSpec#ofConverter): its records, spread over the
    // sample as a spec's dry run spreads them, and the fields they hold. Kept for a few runs, as one check reads the
    // same sample several times.
    private static DryRun converterRun(final String converter, final String sample, final int maxRecords) {
        final List<Object> key = List.of(converter.hashCode(), converter.length(), sample.hashCode(), sample.length(),
                maxRecords);
        synchronized (CONVERTER_RUNS) {
            final DryRun kept = CONVERTER_RUNS.get(key);
            if (kept != null) {
                return kept;
            }
        }
        final StroomSplitterRun.Result result = StroomSplitterRun.run(converter, sample);
        final List<Map<String, String>> all = result.records();
        final List<Map<String, String>> records = new ArrayList<>();
        if (all.size() <= maxRecords) {
            records.addAll(all);
        } else {
            for (int n = 0; n < maxRecords; n++) {
                records.add(all.get((int) ((long) n * all.size() / maxRecords)));
            }
        }
        final Set<String> fields = new LinkedHashSet<>();
        records.forEach(record -> fields.addAll(record.keySet()));
        final DryRun run = new DryRun(records, List.of(), new ArrayList<>(fields), List.of());
        synchronized (CONVERTER_RUNS) {
            CONVERTER_RUNS.put(key, run);
        }
        return run;
    }

    // The patterns a spec gives, for the message if they take too long.
    private static String patterns(final SplitterSpec spec) {
        final List<String> patterns = new ArrayList<>();
        final List<SplitterSpec> specs = new ArrayList<>(List.of(spec));
        for (int n = 0; n < specs.size(); n++) {
            final SplitterSpec part = specs.get(n);
            final List<String> parts = new ArrayList<>(Arrays.asList(part.recordStart(), part.recordEnd(),
                    part.pattern(), part.match(), part.recordKey(), part.lineName()));
            parts.addAll(part.fields().values());
            for (final String pattern : parts) {
                if (pattern != null && !patterns.contains(pattern)) {
                    patterns.add(pattern);
                }
            }
            if (part.body() != null) {
                specs.add(part.body());
            }
            specs.addAll(part.parts());
        }
        return patterns.isEmpty()
                ? null
                : String.join("', '", patterns);
    }

    private static DryRun dryRunLimited(final SplitterSpec spec, final String sample, final int maxRecords) {
        List<String> lines = ToolText.nonBlankLines(sample);
        final boolean delimited = "delimited".equals(spec.kind());
        if (spec.blankLineRecords()) {
            lines = blankLineRecords(sample);
        } else if (spec.recordKey() != null) {
            // Blank lines kept where lines holding no key join a record (a section's blank lines are its own).
            lines = KeyedRecords.group(spec.joinUnkeyed()
                    ? ToolText.splitlines(sample)
                    : lines, spec);
        } else if (spec.recordStart() != null && !delimited) {
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
                heading.add(ToolText.strip(cell));
            }
            lines = lines.subList(1, lines.size());
        }
        // A part's carried field is the latest value before each record, so every record is read in order first.
        final List<Map<String, String>> carriedAt = carriedBefore(spec, lines, heading);
        final List<Integer> positions = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            positions.add(n);
        }
        final List<Map<String, String>> records = new ArrayList<>();
        final List<String> unmatched = new ArrayList<>();
        for (final int position : spread(positions, maxRecords)) {
            final String line = lines.get(position);
            Map<String, String> fields = applyRecord(spec, line, heading);
            if (fields != null && !fields.isEmpty() && carriedAt != null && carriedAt.get(position) != null) {
                fields = new LinkedHashMap<>(fields);
                fields.putAll(carriedAt.get(position));
            }
            if (fields != null && !fields.isEmpty()) {
                records.add(fields);
            } else if (spec.catchAll() && !spec.continuation() && !claimed(spec, line)) {
                // Kept whole, as the converter's catch-all writes it.
                records.add(Map.of(SplitterSpec.UNPARSED, ToolText.rstrip(line, "\n")));
            } else {
                unmatched.add(ToolText.head(line, 200));
            }
        }
        return new DryRun(records, unmatched, fieldNames(records), heading);
    }

    // For an alternatives spec with a part carrying a field (a header's date), the carried values each record of the
    // other parts is written with: the latest the carrying part read before it, as the converter's var holds it.
    // Records before the first carrying record get none. By position (null where none); null for a spec carrying
    // nothing.
    private static List<Map<String, String>> carriedBefore(final SplitterSpec spec,
                                                           final List<String> lines,
                                                           final List<String> heading) {
        if (!SplitterSpec.ALTERNATIVES.equals(spec.kind())
            || spec.parts().stream().noneMatch(part -> part.carry() != null)) {
            return null;
        }
        final Map<String, SplitterSpec> byVariant = new LinkedHashMap<>();
        spec.parts().forEach(part -> byVariant.put(part.variant(), part));
        final List<Map<String, String>> out = new ArrayList<>();
        final Map<String, String> carried = new LinkedHashMap<>();
        for (final String line : lines) {
            final Map<String, String> fields = applyRecord(spec, line, heading);
            final SplitterSpec part = fields == null
                    ? null
                    : byVariant.get(fields.get(spec.variantField()));
            if (part != null && part.carry() != null) {
                part.names().stream().filter(name -> !name.isEmpty()).findFirst()
                        .ifPresent(name -> carried.put(part.carry(), ToolText.strip(fields.getOrDefault(name, ""))));
                out.add(null);
            } else {
                out.add(fields != null && !carried.isEmpty()
                        ? new LinkedHashMap<>(carried)
                        : null);
            }
        }
        return out;
    }

    // As many items as are wanted, spread over them, when there are more.
    private static <T> List<T> spread(final List<T> items, final int most) {
        if (items.size() <= most) {
            return items;
        }
        final List<T> picked = new ArrayList<>();
        for (int i = 0; i < most; i++) {
            picked.add(items.get((int) ToolText.round(i * (items.size() - 1) / (double) (most - 1))));
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

    /// The length of the longest record a spec makes of text over several lines (records that start or end at a
    /// marked line, blocks ended by a blank line, quoted values holding new lines), each with its new line: Stroom's
    /// read buffer must hold a whole record, and such a record can far outrun its longest line (a stack trace of
    /// short lines).
    ///
    /// @param spec The spec.
    /// @param text The text.
    /// @return The longest record's length; 0 when the spec makes one record of each line.
    /// @throws PatternTimeoutException if the spec's record patterns take too long to match.
    public static int longestRecord(final SplitterSpec spec, final String text) {
        final boolean delimited = "delimited".equals(spec.kind());
        final List<String> records;
        if (spec.blankLineRecords()) {
            records = blankLineRecords(text);
        } else if (spec.recordKey() != null) {
            records = KeyedRecords.group(spec.joinUnkeyed()
                    ? ToolText.splitlines(text)
                    : ToolText.nonBlankLines(text), spec);
        } else if (spec.recordStart() != null && !delimited) {
            records = groupLines(ToolText.nonBlankLines(text), spec.recordStart());
        } else if (spec.recordEnd() != null && !delimited) {
            records = groupLinesToEnd(ToolText.nonBlankLines(text), spec.recordEnd());
        } else if (delimited && spec.quotedNewlines() && spec.quote() != null) {
            records = quotedLines(text, spec.quote());
        } else {
            return 0;
        }
        return records.stream().mapToInt(record -> record.length() + 1).max().orElse(0);
    }

    /// Records that are blocks of lines ended by a blank line, as the converter's [#BLANK_LINE_RECORD] takes them
    /// (each with its last line's new line).
    ///
    /// @param text The text.
    /// @return The records.
    public static List<String> blankLineRecords(final String text) {
        final List<String> records = new ArrayList<>();
        final Matcher m = BLANK_LINE_RECORDS.matcher(text);
        while (m.find()) {
            records.add(m.group(1));
        }
        return records;
    }

    /// Records of delimited text whose quoted values may hold new lines: lines joined while a quote is open.
    ///
    /// @param text  The text.
    /// @param quote The quote character.
    /// @return The records.
    public static List<String> quotedLines(final String text, final String quote) {
        final List<String> records = new ArrayList<>();
        String current = null;
        for (final String line : ToolText.splitlines(text)) {
            if (current == null) {
                if (ToolText.isBlank(line)) {
                    continue;
                }
                current = line;
            } else {
                current = current + "\n" + line;
            }
            if (ToolText.count(current, quote) % 2 == 0) {
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
        final Pattern ends = SafePattern.compile(end, ToolText.FLAGS);
        final List<String> records = new ArrayList<>();
        final List<String> current = new ArrayList<>();
        for (final String line : lines) {
            current.add(line);
            if (ToolText.match(ends, new DeadlineCharSequence(line))) {
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
        final Pattern begins = SafePattern.compile(start, ToolText.FLAGS);
        final List<String> records = new ArrayList<>();
        for (final String line : lines) {
            if (records.isEmpty() || ToolText.match(begins, new DeadlineCharSequence(line))) {
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
                for (final String cell : splitQuoted(ToolText.strip(line.substring(spec.headerDirective().length())),
                        spec.delimiter(), spec.quote())) {
                    heading.add(ToolText.strip(cell));
                }
                continue;
            }
            final String directive = spec.directives().stream().filter(line::startsWith).findFirst().orElse(null);
            if (directive != null) {
                held.put(directiveField(directive), ToolText.strip(line.substring(directive.length())));
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
                unmatched.add(ToolText.head(line, 200));
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

        /// @return It as JSON: `records`, `unmatched_lines`, `fields` and `heading`.
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
