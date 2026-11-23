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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// A Mermaid `gantt` chart of the subset Stroom AI writes (the `gantt_chart` tool) and its chat draws: a title, the
/// date format its tasks' times are written in, the format of its axis's ticks, and its sections, each with its
/// tasks. Read by [MermaidGanttParser], on the server and in the browser alike (GWT), so it holds no `java.time`.
///
/// A task's times are wall-clock times (as written, in whatever time zone the title names), held as milliseconds
/// since 1970-01-01 00:00 on that clock ([#wallClock]): what the chart draws, with no zone to convert from.
public final class MermaidGantt {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    /// The milliseconds in a day.
    public static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;

    private final String title;
    private final String dateFormat;
    private final String axisFormat;
    private final List<Section> sections;

    /// @param title      The chart's title, or null.
    /// @param dateFormat The format its tasks' times are written in.
    /// @param axisFormat The format of its axis's ticks (d3's), or null for Mermaid's default.
    /// @param sections   Its sections, in order.
    public MermaidGantt(final String title,
                        final String dateFormat,
                        final String axisFormat,
                        final List<Section> sections) {
        this.title = title;
        this.dateFormat = dateFormat;
        this.axisFormat = axisFormat;
        this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
    }

    /// @return The chart's title, or null.
    public String getTitle() {
        return title;
    }

    /// @return The format its tasks' times are written in (dayjs tokens, e.g. `YYYY-MM-DD HH:mm:ss`).
    public String getDateFormat() {
        return dateFormat;
    }

    /// @return The format of its axis's ticks (d3 tokens, e.g. `%H:%M`), or null for Mermaid's default.
    public String getAxisFormat() {
        return axisFormat;
    }

    /// @return Its sections, in order.
    public List<Section> getSections() {
        return sections;
    }

    /// @return How many tasks it has, in all its sections.
    public int taskCount() {
        int count = 0;
        for (final Section section : sections) {
            count += section.tasks().size();
        }
        return count;
    }

    /// @param year   The year.
    /// @param month  The month, 1 to 12.
    /// @param day    The day of the month, from 1.
    /// @param hour   The hour, 0 to 23.
    /// @param minute The minute, 0 to 59.
    /// @param second The second, 0 to 59.
    /// @param millis The millisecond, 0 to 999.
    /// @return The time as milliseconds since 1970-01-01 00:00 on the same clock.
    public static long wallClock(final int year,
                                 final int month,
                                 final int day,
                                 final int hour,
                                 final int minute,
                                 final int second,
                                 final int millis) {
        return daysFromCivil(year, month, day) * MILLIS_PER_DAY
               + hour * MILLIS_PER_HOUR
               + minute * MILLIS_PER_MINUTE
               + second * MILLIS_PER_SECOND
               + millis;
    }

    /// @param wallClock A time as [#wallClock] gives it.
    /// @return Its fields: year, month (1 to 12), day, hour, minute, second, millisecond, and day of the week
    /// (0 Sunday to 6 Saturday).
    public static int[] fieldsOf(final long wallClock) {
        final long days = floorDiv(wallClock, MILLIS_PER_DAY);
        final long ofDay = wallClock - days * MILLIS_PER_DAY;
        final int[] date = civilFromDays(days);
        return new int[]{
                date[0],
                date[1],
                date[2],
                (int) (ofDay / MILLIS_PER_HOUR),
                (int) (ofDay % MILLIS_PER_HOUR / MILLIS_PER_MINUTE),
                (int) (ofDay % MILLIS_PER_MINUTE / MILLIS_PER_SECOND),
                (int) (ofDay % MILLIS_PER_SECOND),
                (int) floorMod(days + 4, 7)};
    }

    /// @param year  The year.
    /// @param month The month, 1 to 12.
    /// @return How many days the month has.
    public static int daysInMonth(final int year, final int month) {
        if (month == 2) {
            final boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
            return leap
                    ? 29
                    : 28;
        }
        return month == 4 || month == 6 || month == 9 || month == 11
                ? 30
                : 31;
    }

    // Days since 1970-01-01 of a date in the proleptic Gregorian calendar (Howard Hinnant's days_from_civil).
    private static long daysFromCivil(final int year, final int month, final int day) {
        final long y = month <= 2
                ? year - 1
                : year;
        final long era = floorDiv(y, 400);
        final long yearOfEra = y - era * 400;
        final long dayOfYear = (153L * (month + (month > 2
                ? -3
                : 9)) + 2) / 5 + day - 1;
        final long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146_097 + dayOfEra - 719_468;
    }

    // The year, month and day of a count of days since 1970-01-01 (Howard Hinnant's civil_from_days).
    private static int[] civilFromDays(final long days) {
        final long z = days + 719_468;
        final long era = floorDiv(z, 146_097);
        final long dayOfEra = z - era * 146_097;
        final long yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
        final long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        final long mp = (5 * dayOfYear + 2) / 153;
        final int day = (int) (dayOfYear - (153 * mp + 2) / 5 + 1);
        final int month = (int) (mp < 10
                ? mp + 3
                : mp - 9);
        final int year = (int) (yearOfEra + era * 400 + (month <= 2
                ? 1
                : 0));
        return new int[]{year, month, day};
    }

    // As Math.floorDiv, written out so the browser's build needs no emulation of it.
    private static long floorDiv(final long a, final long b) {
        final long quotient = a / b;
        return (a % b != 0 && ((a < 0) != (b < 0)))
                ? quotient - 1
                : quotient;
    }

    private static long floorMod(final long a, final long b) {
        return a - floorDiv(a, b) * b;
    }


    // --------------------------------------------------------------------------------


    /// A section of the chart: a lane of tasks, e.g. a host or a day.
    ///
    /// @param name  Its name, or empty for the tasks before the first section.
    /// @param tasks Its tasks, in order.
    public record Section(String name, List<Task> tasks) {

        /// @param name  Its name, or empty for the tasks before the first section.
        /// @param tasks Its tasks, in order.
        public Section {
            tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        }
    }


    // --------------------------------------------------------------------------------


    /// A task: a bar from its start to its end, or a marker at its start if it is a milestone or has no length.
    ///
    /// @param name  What it is called.
    /// @param id    Its id, or null.
    /// @param tags  Its tags (`done`, `active`, `crit`, `milestone`), in the order given.
    /// @param start When it starts, as [MermaidGantt#wallClock] gives it.
    /// @param end   When it ends, the same way, not before its start.
    public record Task(String name, String id, List<String> tags, long start, long end) {

        /// The tag that makes a task a marker.
        public static final String MILESTONE = "milestone";

        /// @param name  What it is called.
        /// @param id    Its id, or null.
        /// @param tags  Its tags, in the order given.
        /// @param start When it starts.
        /// @param end   When it ends, not before its start.
        public Task {
            tags = Collections.unmodifiableList(new ArrayList<>(tags));
        }

        /// @return True if it is drawn as a marker at its start: a milestone, or a task with no length.
        public boolean isMarker() {
            return end == start || tags.contains(MILESTONE);
        }
    }
}
