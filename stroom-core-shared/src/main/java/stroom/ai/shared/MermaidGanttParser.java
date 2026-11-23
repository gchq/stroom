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

package stroom.ai.shared;

import stroom.ai.shared.MermaidGantt.Section;
import stroom.ai.shared.MermaidGantt.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// Reads a Mermaid `gantt` chart of the subset Stroom AI writes and draws, and nothing else: one grammar for the
/// `gantt_chart` tool, which checks every block it writes reads back (a round trip), and the chat, which draws a
/// block that reads and shows any other as code, never run. Plain Java with no regular expressions or `java.time`,
/// as it runs in the browser too (GWT).
///
/// The subset, a statement a line (Mermaid's own syntax, so other apps draw it the same):
///
/// * `gantt`, first;
/// * `title <text>`, at most once;
/// * `dateFormat <format>`, before any task: dayjs tokens `YYYY`, `MM`, `DD`, `HH`, `mm`, `ss` and `SSS` (the
///   year, month and day required), separated by `-`, `/`, `:`, `.`, `T` or spaces; `YYYY-MM-DD` if not given;
/// * `axisFormat <format>`: d3 tokens `%Y`, `%y`, `%m`, `%d`, `%e`, `%b`, `%a`, `%H`, `%M` and `%S`, and text;
/// * `section <name>`;
/// * a task, `<name> : [tags,] [id,] <start>, <end or duration>`: tags `done`, `active`, `crit` and `milestone`,
///   an id of letters, digits, `_` and `-`, the start in the date format, the end in it too or a duration (a
///   number and `ms`, `s`, `m`, `h`, `d` or `w`); a milestone or a task with no length is a marker.
///
/// Anything else is outside it: comments and directives (`%%`, so never `%%{init}%%`), `click`, `call` and
/// callbacks, `after` and `until`, `excludes`, `todayMarker` and Mermaid's other statements, front matter, and
/// the characters Mermaid reads as syntax in text (`#`, `;`, `%`, quotes, braces, angle brackets, backticks and
/// backslashes). So is a task whose name Mermaid would read as a statement or a date (one starting with a
/// keyword, or with `2026-11-23`): [#startsLikeAStatement(String)] says which, for the tool to avoid.
public final class MermaidGanttParser {

    /// The longest block read: past it, a block is shown as code.
    public static final int MAX_CHARS = 200_000;
    /// The most tasks a block may have.
    public static final int MAX_TASKS = 1_000;
    /// The most sections a block may have.
    public static final int MAX_SECTIONS = 200;
    /// The date format of a block that doesn't give one, as Mermaid's.
    public static final String DEFAULT_DATE_FORMAT = "YYYY-MM-DD";
    /// The tags a task may have.
    public static final List<String> TAGS = Collections.unmodifiableList(Arrays.asList(
            "done", "active", "crit", Task.MILESTONE));
    /// What Mermaid's gantt grammar reads at the start of a statement as a keyword (in any case), whatever follows
    /// it: a task's name must not start with one.
    public static final List<String> KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "gantt", "title", "dateformat", "axisformat", "section", "click", "call", "href", "excludes",
            "includes", "todaymarker", "tickinterval", "weekday", "weekend", "inclusiveenddates", "topaxis",
            "acctitle", "accdescr", "accdescription", "displaymode"));
    /// The characters Mermaid reads as syntax in text, never in a block of the subset.
    public static final String SYNTAX_CHARACTERS = "#;%\"'`{}<>\\";

    private static final String GANTT = "gantt";
    private static final String TITLE = "title";
    private static final String DATE_FORMAT = "dateformat";
    private static final String AXIS_FORMAT = "axisformat";
    private static final String SECTION = "section";
    // The dayjs tokens of the subset (case matters: MM is the month, mm the minute).
    private static final List<String> DATE_TOKENS = Collections.unmodifiableList(Arrays.asList(
            "YYYY", "MM", "DD", "HH", "mm", "ss", "SSS"));
    private static final String DATE_SEPARATORS = "-/:. T";
    private static final String AXIS_TOKENS = "YymdebaHMS";

    private MermaidGanttParser() {
        // Static only.
    }

    /// @param text A block's text, e.g. a ```` ```mermaid ```` block's content.
    /// @return The chart it holds.
    /// @throws IllegalArgumentException if it isn't a gantt chart of the subset, saying where and why.
    public static MermaidGantt parse(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("No text");
        } else if (text.length() > MAX_CHARS) {
            throw new IllegalArgumentException("Longer than " + MAX_CHARS + " characters");
        }
        final List<String> lines = lines(text);
        final Builder builder = new Builder();
        boolean started = false;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                if (!started) {
                    if (!GANTT.equals(line.toLowerCase())) {
                        throw new IllegalArgumentException("not a gantt chart: it must start with 'gantt'");
                    }
                    started = true;
                } else {
                    statement(line, builder);
                }
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        if (!started) {
            throw new IllegalArgumentException("Empty");
        }
        return builder.build();
    }

    /// @param text A block's text, or null.
    /// @return True if it is a gantt chart of the subset.
    public static boolean isValid(final String text) {
        try {
            parse(text);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    /// @param name A task's name, as it would be written.
    /// @return True if Mermaid would read a task line starting with it as another statement or a date: it starts
    /// with a keyword ([#KEYWORDS], in any case) or a date (`dddd-dd-dd`).
    public static boolean startsLikeAStatement(final String name) {
        final String lower = name.trim().toLowerCase();
        for (final String keyword : KEYWORDS) {
            if (lower.startsWith(keyword)) {
                return true;
            }
        }
        return lower.length() >= 10
               && digits(lower, 0, 4) && lower.charAt(4) == '-'
               && digits(lower, 5, 2) && lower.charAt(7) == '-'
               && digits(lower, 8, 2);
    }

    // One statement after `gantt`.
    private static void statement(final String line, final Builder builder) {
        final String keyword = firstWord(line).toLowerCase();
        final String rest = line.substring(Math.min(line.length(), keyword.length())).trim();
        final boolean followedBySpace = line.length() > keyword.length()
                                        && isSpace(line.charAt(keyword.length()));
        if (AXIS_FORMAT.equals(keyword) && followedBySpace) {
            checkCharacters(line, "%");
            builder.axisFormat(axisFormat(rest));
            return;
        }
        checkCharacters(line, "");
        if (TITLE.equals(keyword) && followedBySpace) {
            builder.title(rest);
        } else if (DATE_FORMAT.equals(keyword) && followedBySpace) {
            builder.dateFormat(dateFormat(rest));
        } else if (SECTION.equals(keyword) && followedBySpace) {
            builder.section(rest);
        } else if (startsLikeAStatement(line)) {
            throw new IllegalArgumentException("'" + keyword + "' is outside the subset");
        } else {
            builder.task(line);
        }
    }

    // Refuses a line with a character Mermaid reads as syntax in text, but those allowed.
    private static void checkCharacters(final String line, final String allowed) {
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if ((SYNTAX_CHARACTERS.indexOf(c) >= 0 && allowed.indexOf(c) < 0) || (c < ' ' && c != '\t')) {
                throw new IllegalArgumentException("the character '" + c + "' is outside the subset");
            }
        }
    }

    // The dayjs date format a block's times are in, checked: only the tokens and separators of the subset.
    private static String dateFormat(final String format) {
        final List<String> tokens = dateTokens(format);
        if (!tokens.contains("YYYY") || !tokens.contains("MM") || !tokens.contains("DD")) {
            throw new IllegalArgumentException("a date format needs YYYY, MM and DD");
        }
        return format;
    }

    // The date format's parts: each token, and each separator as itself.
    private static List<String> dateTokens(final String format) {
        final List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < format.length()) {
            final String token = dateTokenAt(format, i);
            if (token != null) {
                if (parts.contains(token)) {
                    throw new IllegalArgumentException("the date format has " + token + " twice");
                }
                parts.add(token);
                i += token.length();
            } else if (DATE_SEPARATORS.indexOf(format.charAt(i)) >= 0) {
                parts.add(String.valueOf(format.charAt(i)));
                i++;
            } else {
                throw new IllegalArgumentException("the date format '" + format + "' is outside the subset");
            }
        }
        return parts;
    }

    private static String dateTokenAt(final String format, final int i) {
        for (final String token : DATE_TOKENS) {
            if (format.startsWith(token, i)) {
                return token;
            }
        }
        return null;
    }

    // The d3 axis format, checked: only the tokens of the subset.
    private static String axisFormat(final String format) {
        for (int i = 0; i < format.length(); i++) {
            if (format.charAt(i) == '%') {
                if (i + 1 >= format.length() || AXIS_TOKENS.indexOf(format.charAt(i + 1)) < 0) {
                    throw new IllegalArgumentException("the axis format '" + format + "' is outside the subset");
                }
                i++;
            }
        }
        return format;
    }

    /// Reads a time strictly, as Mermaid does: each number with as many digits as its token, each separator as it
    /// is, and a date there is.
    ///
    /// @param text   The time as written.
    /// @param format The date format of the subset it is in.
    /// @return It as [MermaidGantt#wallClock] gives it.
    /// @throws IllegalArgumentException if it isn't a time in the format.
    static long time(final String text, final String format) {
        final List<String> parts = dateTokens(format);
        final int[] fields = {1970, 1, 1, 0, 0, 0, 0};
        int at = 0;
        for (final String part : parts) {
            final int field = fieldOf(part);
            if (field < 0) {
                if (!text.startsWith(part, at)) {
                    throw new IllegalArgumentException("'" + text + "' isn't a time in the format " + format);
                }
                at += part.length();
            } else {
                if (!digits(text, at, part.length())) {
                    throw new IllegalArgumentException("'" + text + "' isn't a time in the format " + format);
                }
                fields[field] = Integer.parseInt(text.substring(at, at + part.length()));
                at += part.length();
            }
        }
        if (at != text.length()) {
            throw new IllegalArgumentException("'" + text + "' isn't a time in the format " + format);
        }
        if (fields[1] < 1 || fields[1] > 12
            || fields[2] < 1 || fields[2] > MermaidGantt.daysInMonth(fields[0], fields[1])
            || fields[3] > 23 || fields[4] > 59 || fields[5] > 59) {
            throw new IllegalArgumentException("'" + text + "' isn't a date and time there is");
        }
        return MermaidGantt.wallClock(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6]);
    }

    // The index of a token's field: year, month, day, hour, minute, second, millisecond.
    private static int fieldOf(final String token) {
        return switch (token) {
            case "YYYY" -> 0;
            case "MM" -> 1;
            case "DD" -> 2;
            case "HH" -> 3;
            case "mm" -> 4;
            case "ss" -> 5;
            case "SSS" -> 6;
            default -> -1;
        };
    }

    /// @param text A task's end as written.
    /// @return It as a duration (a number and `ms`, `s`, `m`, `h`, `d` or `w`) in milliseconds, or -1 if it isn't
    /// one.
    static long duration(final String text) {
        int i = 0;
        while (i < text.length() && (isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
            i++;
        }
        if (i == 0 || i == text.length()) {
            return -1;
        }
        final String number = text.substring(0, i);
        if (number.startsWith(".") || number.endsWith(".") || number.indexOf('.') != number.lastIndexOf('.')
            || number.length() > 9) {
            return -1;
        }
        final long unit = switch (text.substring(i)) {
            case "ms" -> 1L;
            case "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> MermaidGantt.MILLIS_PER_DAY;
            case "w" -> 7 * MermaidGantt.MILLIS_PER_DAY;
            default -> -1L;
        };
        return unit < 0
                ? -1
                : Math.round(Double.parseDouble(number) * unit);
    }

    private static List<String> lines(final String text) {
        final List<String> lines = new ArrayList<>();
        int from = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String line = text.substring(from, i);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
                from = i + 1;
            }
        }
        return lines;
    }

    // The letters a line starts with.
    private static String firstWord(final String line) {
        int i = 0;
        while (i < line.length() && isLetter(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private static List<String> items(final String data) {
        final List<String> items = new ArrayList<>();
        int from = 0;
        for (int i = 0; i <= data.length(); i++) {
            if (i == data.length() || data.charAt(i) == ',') {
                items.add(data.substring(from, i).trim());
                from = i + 1;
            }
        }
        return items;
    }

    private static boolean isId(final String text) {
        if (text.isEmpty() || !isLetter(text.charAt(0))) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!isLetter(c) && !isDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean digits(final String text, final int from, final int count) {
        if (from + count > text.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            if (!isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isSpace(final char c) {
        return c == ' ' || c == '\t';
    }


    // --------------------------------------------------------------------------------


    // The chart as it is read.
    private static final class Builder {

        private final List<Section> sections = new ArrayList<>();
        private String title;
        private String dateFormat;
        private String axisFormat;
        private String sectionName = "";
        private List<Task> tasks = new ArrayList<>();
        private int taskCount;
        private boolean anyTask;

        private void title(final String text) {
            if (title != null) {
                throw new IllegalArgumentException("a second title");
            }
            title = text;
        }

        private void dateFormat(final String format) {
            if (dateFormat != null) {
                throw new IllegalArgumentException("a second date format");
            } else if (anyTask) {
                throw new IllegalArgumentException("the date format must come before the tasks");
            }
            dateFormat = format;
        }

        private void axisFormat(final String format) {
            if (axisFormat != null) {
                throw new IllegalArgumentException("a second axis format");
            }
            axisFormat = format;
        }

        private void section(final String name) {
            endSection();
            if (sections.size() >= MAX_SECTIONS) {
                throw new IllegalArgumentException("more than " + MAX_SECTIONS + " sections");
            }
            sectionName = name;
        }

        private void task(final String line) {
            final int colon = line.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("a task needs ':' after its name");
            }
            final String name = line.substring(0, colon).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("a task needs a name");
            }
            final List<String> items = items(line.substring(colon + 1));
            final List<String> tags = new ArrayList<>();
            while (!items.isEmpty() && TAGS.contains(items.get(0))) {
                if (tags.contains(items.get(0))) {
                    throw new IllegalArgumentException("the tag " + items.get(0) + " twice");
                }
                tags.add(items.remove(0));
            }
            if (items.size() != 2 && items.size() != 3) {
                throw new IllegalArgumentException("a task needs a start and an end or duration, after an "
                                                   + "optional id");
            }
            final String id = items.size() == 3
                    ? items.remove(0)
                    : null;
            if (id != null && !isId(id)) {
                throw new IllegalArgumentException("'" + id + "' isn't an id");
            }
            final String format = dateFormat == null
                    ? DEFAULT_DATE_FORMAT
                    : dateFormat;
            final long start = time(items.get(0), format);
            final long length = duration(items.get(1));
            final long end = length >= 0
                    ? start + length
                    : time(items.get(1), format);
            if (end < start) {
                throw new IllegalArgumentException("the task ends before it starts");
            }
            if (++taskCount > MAX_TASKS) {
                throw new IllegalArgumentException("more than " + MAX_TASKS + " tasks");
            }
            anyTask = true;
            tasks.add(new Task(name, id, tags, start, end));
        }

        private void endSection() {
            if (!tasks.isEmpty() || !sectionName.isEmpty()) {
                sections.add(new Section(sectionName, tasks));
            }
            tasks = new ArrayList<>();
        }

        private MermaidGantt build() {
            endSection();
            return new MermaidGantt(title, dateFormat == null
                    ? DEFAULT_DATE_FORMAT
                    : dateFormat, axisFormat, sections);
        }
    }
}
