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

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Records before formats: a text sample's lines grouped into records (an entry and the lines that continue it, a
/// stack trace or a wrapped message), and the format of each record's first line, so a format is judged on records
/// and a stream whose lines come in several formats is told from one that has none.
///
/// Seen in testing: a stream of syslog, key=value and plain log lines read as text, line by line, its parser left
/// to the agent; and stack traces, each frame a line, outnumbering the entries they belong to, so no format reached
/// its share of the lines.
public final class LineFormats {

    /// A line that starts a record: any line that doesn't continue the one before it. A line continues it when it is
    /// indented, or is a stack trace's frame (`at ...`), cause (`Caused by:`), suppressed exception, elision
    /// (`... 12 more`) or exception line (`java.lang.IllegalStateException: boom`, a Python traceback's last,
    /// `ValueError: bad input`, but not `Error: disk full`), or a Python traceback's heading.
    /// The same regex groups the profile's lines, the dry run's and the generated Data Splitter's.
    public static final String RECORD_LINE_START = "(?![ \\t]|at [\\w$.<>]+\\(|Caused by: |Suppressed: "
                                                   + "|\\.\\.\\. \\d+ |[\\w$]+(?:\\.[\\w$]+)+(?:Exception|Error"
                                                   + "|Throwable)(?::|$)|[A-Z][a-z][\\w$]*(?:Exception|Error)(?::|$)"
                                                   + "|Traceback \\(most recent call last\\):)\\S";
    /// The format of a line that is timestamped text: a time, then free text.
    public static final String TIMESTAMPED_TEXT = "timestamped text";
    /// The format of a line holding a date for the lines after it whose times have none (a file's header).
    public static final String DATE_HEADER = "date header";
    /// A time of day with no date, at a line's start.
    public static final String TIME_ONLY = "\\d{2}:\\d{2}:\\d{2}";
    /// The dates a header line may hold.
    public static final List<String> DATES = List.of("\\d{4}-\\d{2}-\\d{2}", "\\d{1,2}/\\d{1,2}/\\d{4}",
            "\\d{1,2}\\.\\d{1,2}\\.\\d{4}", "\\d{1,2} [A-Z][a-z]{2} \\d{4}", "[A-Z][a-z]{2} \\d{1,2},? \\d{4}");
    /// The share of records each of two or more formats needs for a stream to be of mixed lines.
    public static final double MIXED_SHARE = 0.1;

    private static final Pattern STARTS = ToolText.re("^(?:" + RECORD_LINE_START + ")");
    private static final Pattern TIME_ONLY_START = ToolText.re("^" + TIME_ONLY + "(?:[.,]\\d+)?\\s");
    private static final int EXAMPLES = 2;
    private static final Pattern PAIR_START = ToolText.re("^\\s*[A-Za-z_][\\w.-]*=");

    private LineFormats() {
        // Static only.
    }

    /// The lines grouped into records, when some line continues the one before it.
    ///
    /// @param lines A sample's non-blank lines.
    /// @return The records and how many have lines after their first; null when no line continues another, or
    /// fewer than two lines start records (an indented layout, not entries and their traces). Lines before the first
    /// that starts one (a stream cut in a stack trace) are a record of their own, as the splitter leaves them.
    public static Grouped group(final List<String> lines) {
        final long starts = lines.stream().filter(LineFormats::startsRecord).count();
        if (starts < 2 || starts == lines.size()) {
            return null;
        }
        final List<String> records = SplitterDryRun.groupLines(lines, RECORD_LINE_START);
        final int continued = (int) records.stream().filter(record -> record.indexOf('\n') >= 0).count();
        return new Grouped(records, continued);
    }

    /// @param line A line.
    /// @return Whether it starts a record ([#RECORD_LINE_START]).
    public static boolean startsRecord(final String line) {
        return ToolText.match(STARTS, line);
    }

    /// @param records Records.
    /// @return Each one's first line.
    public static List<String> firstLines(final List<String> records) {
        return records.stream()
                .map(record -> record.indexOf('\n') < 0
                        ? record
                        : record.substring(0, record.indexOf('\n')))
                .toList();
    }

    /// The format a record's first line is in, as the profile tells formats apart: CEF (alone or after a syslog
    /// header), a syslog header, key=value pairs (three or more), or a time then text.
    ///
    /// @param line A record's first line.
    /// @return Its format ([SampleProfiler#CEF_FORMAT], [SampleProfiler#SYSLOG_5424_FORMAT],
    /// [SampleProfiler#SYSLOG_3164_FORMAT], [SampleProfiler#KEY_VALUE_FORMAT] or [#TIMESTAMPED_TEXT]), or null
    /// when it is none of them.
    public static String formatOf(final String line) {
        if (ToolText.search(SampleProfiler.CEF, line)) {
            return SampleProfiler.CEF_FORMAT;
        }
        if (ToolText.match(SampleProfiler.SYSLOG_5424, line)) {
            return SampleProfiler.SYSLOG_5424_FORMAT;
        }
        if (ToolText.match(SampleProfiler.SYSLOG_3164, line)) {
            return SampleProfiler.SYSLOG_3164_FORMAT;
        }
        // Pairs from its start: a timestamped entry whose message holds pairs (an SQL statement's a=1 AND b=2) is
        // timestamped text, as the key=value part wouldn't read its text before them.
        if (ToolText.match(PAIR_START, line) && SampleProfiler.keyValues(line).size() >= 3) {
            return SampleProfiler.KEY_VALUE_FORMAT;
        }
        return timeStart(line) != null
                ? TIMESTAMPED_TEXT
                : null;
    }

    /// @param line A line.
    /// @return The [SampleProfiler#RECORD_STARTS] regex it starts with, else [#TIME_ONLY] for a time with no date,
    /// or null.
    public static String timeStart(final String line) {
        for (final String start : SampleProfiler.RECORD_STARTS) {
            if (ToolText.match(ToolText.re(start), line)) {
                return start;
            }
        }
        return ToolText.match(TIME_ONLY_START, line)
                ? TIME_ONLY
                : null;
    }

    /// @param line A line.
    /// @return The regex of the date it holds (not at its start as a record's time), e.g. a header's
    /// `Log opened 2026-10-11`; or null.
    public static String dateIn(final String line) {
        for (final String date : DATES) {
            if (ToolText.search(ToolText.re(date), line)) {
                return date;
            }
        }
        return null;
    }

    /// The records by format: [#formatOf]'s, and [#DATE_HEADER] for records holding a date where other records'
    /// times have none (a file's header giving the date of the lines after it).
    ///
    /// @param records The records.
    /// @return Each format's records, in a fixed order (CEF, syslog, key=value, timestamped text, date header), and
    /// those in none.
    public static Classified classify(final List<String> records) {
        final Map<String, List<String>> byFormat = new LinkedHashMap<>();
        for (final String format : List.of(SampleProfiler.CEF_FORMAT, SampleProfiler.SYSLOG_5424_FORMAT,
                SampleProfiler.SYSLOG_3164_FORMAT, SampleProfiler.KEY_VALUE_FORMAT, TIMESTAMPED_TEXT, DATE_HEADER)) {
            byFormat.put(format, new ArrayList<>());
        }
        final List<String> none = new ArrayList<>();
        for (final String record : records) {
            final String format = formatOf(firstLines(List.of(record)).getFirst());
            if (format == null) {
                none.add(record);
            } else {
                byFormat.get(format).add(record);
            }
        }
        final boolean dateless = byFormat.get(TIMESTAMPED_TEXT).stream()
                .anyMatch(record -> TIME_ONLY.equals(timeStart(record)));
        final List<String> unrecognised = new ArrayList<>();
        for (final String record : none) {
            if (dateless && dateIn(firstLines(List.of(record)).getFirst()) != null) {
                byFormat.get(DATE_HEADER).add(record);
            } else {
                unrecognised.add(record);
            }
        }
        byFormat.values().removeIf(List::isEmpty);
        return new Classified(byFormat, unrecognised, dateless);
    }

    /// The profile of a stream whose records come in several formats: two or more of [#formatOf]'s each the format of
    /// at least [#MIXED_SHARE] of its records.
    ///
    /// @param records The records (lines, or grouped records).
    /// @param grouped Whether the records were grouped from lines that continue them.
    /// @return `line_formats` (each `format`, `records`, `share` and `examples`, most records first),
    /// `unrecognised` (`records` and `examples` of the records no format fits), `records`; or null when no two
    /// formats have their share.
    public static Map<String, Object> mixed(final List<String> records, final boolean grouped) {
        final Classified classified = classify(records);
        final Map<String, List<String>> byFormat = classified.byFormat();
        final List<String> unrecognised = classified.unrecognised();
        final List<Map.Entry<String, List<String>>> shared = byFormat.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIXED_SHARE * records.size())
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .toList();
        // Times with no date: read by their own part (with a header giving the date, whatever the header's share: it
        // is a line or two), so the date can be joined with them; otherwise the text would be no format at all.
        final boolean headed = classified.dateless() && byFormat.containsKey(DATE_HEADER);
        if (shared.size() < 2 && !classified.dateless()) {
            return null;
        }
        final List<Map<String, Object>> formats = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : byFormat.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .toList()) {
            final Map<String, Object> format = new LinkedHashMap<>();
            format.put("format", entry.getKey());
            format.put("records", entry.getValue().size());
            format.put("share", ToolText.round(entry.getValue().size() / (double) records.size(), 2));
            format.put("examples", examples(entry.getValue()));
            formats.add(format);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records.size());
        out.put("line_formats", formats);
        if (classified.dateless()) {
            out.put("dateless_times", headed
                    ? "Some records' times have no date; a header line gives it (date header): its date is kept "
                      + "for the records after it, each time joined with it and rolled over midnight"
                    : "Some records' times have no date, and no header line gives one: ask the user where the date "
                      + "comes from (the file name, the stream's received or created time)");
        }
        if (!unrecognised.isEmpty()) {
            out.put("unrecognised", Map.of("records", unrecognised.size(), "examples", examples(unrecognised)));
        }
        out.put("suggested_parser", "Data Splitter with alternatives: a part per format of record, tried in order, "
                                    + "each record's variant written with its fields" + (unrecognised.isEmpty()
                ? ""
                : ", and catch_all keeping the records no part reads")
                                    + (grouped
                ? "; each record's first line read by its part, the lines after it (stack traces) kept as "
                  + SplitterSpec.CONTINUATION
                : "")
                                    + " (build_data_splitter infers it)");
        return out;
    }

    // The first few records, each cut to a few lines: a stack trace is shown by its head.
    private static List<String> examples(final List<String> records) {
        return records.subList(0, Math.min(EXAMPLES, records.size())).stream()
                .map(LineFormats::headOf)
                .toList();
    }

    /// @param record A record.
    /// @return It, or its first lines and how many more there are when it runs over several.
    public static String headOf(final String record) {
        final List<String> lines = List.of(record.split("\n", -1));
        if (lines.size() <= 4) {
            return record;
        }
        return String.join("\n", lines.subList(0, 3)) + "\n... " + (lines.size() - 3) + " more lines";
    }


    // --------------------------------------------------------------------------------


    /// A stream's records by format.
    ///
    /// @param byFormat     Each format's records, in [#classify]'s order, formats with none left out.
    /// @param unrecognised The records in no format.
    /// @param dateless     Whether some timestamped records' times have no date.
    public record Classified(Map<String, List<String>> byFormat, List<String> unrecognised, boolean dateless) {

    }

    /// A sample's lines grouped into records.
    ///
    /// @param records   The records, each its lines joined by new lines.
    /// @param continued How many records have lines after their first.
    public record Grouped(List<String> records, int continued) {

    }
}
