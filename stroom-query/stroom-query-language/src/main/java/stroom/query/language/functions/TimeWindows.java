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

package stroom.query.language.functions;

import stroom.query.api.HoppingWindow;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.Objects;
import java.util.function.LongConsumer;

/// The time windows of a StroomQL `window` clause, as in `window EventTime by 1d advance 1h offset 6h`.
///
/// A window has a size, and a new window starts every slide. With no slide given the slide is the
/// size, so the windows are tumbling and each time is in one window. With a smaller slide the windows
/// are hopping and each time is in every window that covers it, which is `size / slide` windows. A
/// window includes its start and excludes its end.
///
/// How the windows are aligned depends on the unit of the size:
///
/// * Milliseconds to hours are fixed lengths. Window starts are whole multiples of the slide from
///   midnight in the time zone's standard time, which is its time with no daylight saving. `1h`
///   breaks on the hour in every zone, and `24h` is always 24 hours long: it breaks at local midnight
///   in winter and an hour away from it in summer. Where a zone's standard time has itself changed,
///   windows are counted from the standard time in force at each time.
/// * Days and weeks follow the wall clock of the time zone. `1d` runs from local midnight to local
///   midnight, so it is 23 or 25 hours long on a day when the clocks change. Day windows are whole
///   multiples of the slide from 1 January 1970, and week windows from Monday 5 January 1970.
/// * Months and years follow the calendar of the time zone, starting on the first of a month. They
///   are whole multiples of the slide from January 1970.
///
/// The offset moves every window start, later or earlier, so `by 1d offset 6h` runs from 06:00 to
/// 06:00.
///
/// A window is identified by a key, which is a number that only this class gives a meaning to. Keys
/// are in the order of the windows they identify, so rows sorted by key are in time order. Use
/// [#getWindowStart(long)] and [#getWindowEnd(long)] to turn a key into times, which are epoch
/// milliseconds throughout.
///
/// Day, week, month and year windows are worked out on the wall clock, and that has two effects
/// when the clocks change. When they go forward, a window that would start in the hour that is
/// skipped starts when the clocks resume, at the same time as the window after it; the two are
/// still separate windows with separate keys. When they go back, a time in the hour that comes
/// twice is put in windows by its wall clock time, so if a window boundary falls inside that hour,
/// which needs an advance or offset of less than an hour, a time in the second pass can be later
/// than the end that [#getWindowEnd(long)] gives for its window.
public final class TimeWindows {

    /// The most copies of one row that a result store makes for a query with windows, unless it is
    /// configured otherwise. Every row is copied once for each window it is in, so this limits how
    /// much a hopping window multiplies the rows of a query.
    public static final int MAX_WINDOWS_PER_TIME = 100;

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    private static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;
    private static final long MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY;
    // 1 January 1970 was a Thursday, so the first Monday is four days after the epoch.
    private static final long WEEK_ANCHOR_MILLIS = 4 * MILLIS_PER_DAY;
    private static final long MONTHS_PER_YEAR = 12;
    private static final long EPOCH_YEAR = 1970;

    private final Alignment alignment;
    private final ZoneId zoneId;
    // The size and slide, in milliseconds for fixed and local day alignment and in months for month
    // alignment. Window keys are in the same unit: the start in epoch milliseconds for fixed
    // alignment, the start in wall clock milliseconds for local day alignment, and for month alignment
    // the number of months from January 1970.
    private final long size;
    private final long slide;
    // The start that window starts are counted from, in milliseconds. Not used for month alignment.
    private final long anchorMillis;
    // The offset of month aligned windows, which is applied with calendar arithmetic.
    private final SimpleDuration monthOffset;
    private final boolean monthOffsetEarlier;
    private final int windowsPerTime;
    // The standard offset of a zone that has always had the same one, to save looking it up for
    // every time, or null if the zone's has changed.
    private final Long fixedStandardOffsetMillis;

    private TimeWindows(final Alignment alignment,
                        final ZoneId zoneId,
                        final long size,
                        final long slide,
                        final long anchorMillis,
                        final SimpleDuration monthOffset,
                        final boolean monthOffsetEarlier) {
        this.alignment = alignment;
        this.zoneId = zoneId;
        this.size = size;
        this.slide = slide;
        this.anchorMillis = anchorMillis;
        this.monthOffset = monthOffset;
        this.monthOffsetEarlier = monthOffsetEarlier;
        this.windowsPerTime = (int) (size / slide);
        this.fixedStandardOffsetMillis = zoneId.getRules().isFixedOffset()
                ? zoneId.getRules().getStandardOffset(Instant.EPOCH).getTotalSeconds() * MILLIS_PER_SECOND
                : null;
    }

    /// Creates the windows that a `window` clause was parsed into.
    ///
    /// @param window The parsed clause.
    /// @return The windows.
    /// @throws IllegalArgumentException If the clause can't be used. The message says why, in words
    ///                                  fit to show the query's author.
    public static TimeWindows create(final HoppingWindow window) {
        Objects.requireNonNull(window, "Window is null");
        final String offset = window.getOffset();
        final boolean offsetEarlier = offset != null && offset.startsWith("-");
        final String unsignedOffset = offsetEarlier
                ? offset.substring(1)
                : offset;
        final ZoneId zoneId;
        try {
            zoneId = window.getTimeZone() == null
                    ? ZoneOffset.UTC
                    : ZoneId.of(window.getTimeZone());
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Unknown time zone '" + window.getTimeZone() + "'", e);
        }
        return create(
                parseDuration(window.getWindowSize(), "window size"),
                window.getAdvanceSize() == null
                        ? null
                        : parseDuration(window.getAdvanceSize(), "advance"),
                unsignedOffset == null
                        ? null
                        : parseDuration(unsignedOffset, "offset"),
                offsetEarlier,
                zoneId);
    }

    private static SimpleDuration parseDuration(final String string, final String name) {
        try {
            return SimpleDurationUtil.parse(string);
        } catch (final ParseException | RuntimeException e) {
            throw new IllegalArgumentException(
                    "The " + name + " '" + string + "' is not a duration, such as 1d", e);
        }
    }

    /// Creates the windows of a `window` clause.
    ///
    /// @param size          The size of each window, as in `by 1d`.
    /// @param advance       The slide between window starts, as in `advance 1h`, or `null` for
    ///                      tumbling windows.
    /// @param offset        How far to move every window start, as in `offset 6h`, or `null` for none.
    /// @param offsetEarlier Whether the offset moves window starts earlier, as in `offset -6h`, and
    ///                      not later.
    /// @param zoneId        The time zone that windows follow.
    /// @return The windows.
    /// @throws IllegalArgumentException If the size, advance and offset can't be used together. The
    ///                                  message says why, in words fit to show the query's author.
    public static TimeWindows create(final SimpleDuration size,
                                     final SimpleDuration advance,
                                     final SimpleDuration offset,
                                     final boolean offsetEarlier,
                                     final ZoneId zoneId) {
        Objects.requireNonNull(size, "Window size is null");
        Objects.requireNonNull(zoneId, "Time zone is null");
        final SimpleDuration slide = Objects.requireNonNullElse(advance, size);
        checkPositive(size, "window size");
        checkPositive(slide, "advance");

        final Alignment alignment = Alignment.of(size.getTimeUnit());
        try {
            return switch (alignment) {
                case FIXED -> createFixed(size, slide, offset, offsetEarlier, zoneId);
                case LOCAL_DAY -> createLocalDay(size, slide, offset, offsetEarlier, zoneId);
                case MONTH -> createMonth(size, slide, offset, offsetEarlier, zoneId);
            };
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException("The window size, advance or offset is too large", e);
        }
    }

    private static TimeWindows createFixed(final SimpleDuration size,
                                           final SimpleDuration slide,
                                           final SimpleDuration offset,
                                           final boolean offsetEarlier,
                                           final ZoneId zoneId) {
        if (Alignment.of(slide.getTimeUnit()) != Alignment.FIXED) {
            throw new IllegalArgumentException("The advance of a window of " + size
                                               + " must be in hours or a smaller unit, but is " + slide);
        }
        if (offset != null && Alignment.of(offset.getTimeUnit()) != Alignment.FIXED) {
            throw new IllegalArgumentException("The offset of a window of " + size
                                               + " must be in hours or a smaller unit, but is " + offset);
        }
        final long sizeMillis = toFixedMillis(size);
        final long slideMillis = toFixedMillis(slide);
        checkWholeMultiple(size, slide, sizeMillis, slideMillis);
        final long offsetMillis = toOffsetMillis(offset, offsetEarlier);
        return new TimeWindows(Alignment.FIXED, zoneId, sizeMillis, slideMillis, offsetMillis, null, false);
    }

    private static TimeWindows createLocalDay(final SimpleDuration size,
                                              final SimpleDuration slide,
                                              final SimpleDuration offset,
                                              final boolean offsetEarlier,
                                              final ZoneId zoneId) {
        // A week is a whole number of days, so a week window can advance by days or less. A day
        // window can't advance by weeks, which are counted from a different day.
        final boolean weeks = size.getTimeUnit() == TimeUnit.WEEKS;
        final TimeUnit slideUnit = slide.getTimeUnit();
        final boolean slideAllowed = Alignment.of(slideUnit) == Alignment.FIXED
                                     || slideUnit == TimeUnit.DAYS
                                     || (weeks && slideUnit == TimeUnit.WEEKS);
        if (!slideAllowed) {
            throw new IllegalArgumentException("The advance of a window of " + size + " must be in "
                                               + (weeks
                                                       ? "weeks"
                                                       : "days")
                                               + " or a smaller unit, but is " + slide);
        }
        if (offset != null && Alignment.of(offset.getTimeUnit()) == Alignment.MONTH) {
            throw new IllegalArgumentException("The offset of a window of " + size
                                               + " must be in weeks or a smaller unit, but is " + offset);
        }
        final long sizeMillis = toFixedMillis(size);
        final long slideMillis = toFixedMillis(slide);
        checkWholeMultiple(size, slide, sizeMillis, slideMillis);
        final long offsetMillis = toOffsetMillis(offset, offsetEarlier);
        final long anchorMillis = weeks
                ? WEEK_ANCHOR_MILLIS
                : 0;
        return new TimeWindows(
                Alignment.LOCAL_DAY, zoneId, sizeMillis, slideMillis, anchorMillis + offsetMillis, null, false);
    }

    private static TimeWindows createMonth(final SimpleDuration size,
                                           final SimpleDuration slide,
                                           final SimpleDuration offset,
                                           final boolean offsetEarlier,
                                           final ZoneId zoneId) {
        // A year is a whole number of months, so a year window can advance by months. Neither can
        // advance by days or less, as a month is not a whole number of them.
        if (Alignment.of(slide.getTimeUnit()) != Alignment.MONTH) {
            throw new IllegalArgumentException("The advance of a window of " + size
                                               + " must be in months or years, but is " + slide);
        }
        if (offset != null) {
            // Checks the unit is one that windows support.
            Alignment.of(offset.getTimeUnit());
        }
        final long sizeMonths = toMonths(size);
        final long slideMonths = toMonths(slide);
        checkWholeMultiple(size, slide, sizeMonths, slideMonths);
        return new TimeWindows(Alignment.MONTH, zoneId, sizeMonths, slideMonths, 0, offset, offsetEarlier);
    }

    private static void checkPositive(final SimpleDuration duration, final String name) {
        if (duration.getTime() <= 0) {
            throw new IllegalArgumentException("The " + name + " must be more than zero, but is " + duration);
        }
    }

    private static void checkWholeMultiple(final SimpleDuration size,
                                           final SimpleDuration slide,
                                           final long sizeLength,
                                           final long slideLength) {
        if (slideLength > sizeLength) {
            throw new IllegalArgumentException("The advance of a window can't be more than its size, but "
                                               + slide + " is more than " + size);
        }
        if (sizeLength / slideLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The advance of a window is too small for its size: " + size
                                               + " is more than " + Integer.MAX_VALUE + " times " + slide);
        }
        if (sizeLength % slideLength != 0) {
            throw new IllegalArgumentException("The size of a window must be a whole multiple of its "
                                               + "advance, but " + size + " is not a multiple of " + slide);
        }
    }

    private static long toFixedMillis(final SimpleDuration duration) {
        final long unitMillis = switch (duration.getTimeUnit()) {
            case MILLISECONDS -> 1;
            case SECONDS -> MILLIS_PER_SECOND;
            case MINUTES -> MILLIS_PER_MINUTE;
            case HOURS -> MILLIS_PER_HOUR;
            case DAYS -> MILLIS_PER_DAY;
            case WEEKS -> MILLIS_PER_WEEK;
            default -> throw new IllegalArgumentException("Unexpected unit of " + duration);
        };
        return Math.multiplyExact(duration.getTime(), unitMillis);
    }

    private static long toOffsetMillis(final SimpleDuration offset, final boolean offsetEarlier) {
        if (offset == null) {
            return 0;
        }
        final long millis = toFixedMillis(offset);
        return offsetEarlier
                ? -millis
                : millis;
    }

    private static long toMonths(final SimpleDuration duration) {
        return duration.getTimeUnit() == TimeUnit.YEARS
                ? Math.multiplyExact(duration.getTime(), MONTHS_PER_YEAR)
                : duration.getTime();
    }

    /// Gets how many times a row is added to a result store, which is once for each window it is in
    /// and once more for each of those for every other window that a function such as `lag` reads.
    ///
    /// @param windowsPerTime How many windows each time is in.
    /// @param windowShifts   How many other windows the functions of the query read.
    /// @return The number of copies of each row.
    public static long getCopiesPerTime(final int windowsPerTime, final int windowShifts) {
        return (long) windowsPerTime * (windowShifts + 1);
    }

    /// Says why a query makes too many copies of each row, in words fit to show its author.
    ///
    /// @param windowsPerTime How many windows each time is in.
    /// @param windowShifts   How many other windows the functions of the query read.
    /// @param maxCopies      The most copies allowed.
    /// @return The message.
    public static String tooManyCopiesMessage(final int windowsPerTime,
                                              final int windowShifts,
                                              final int maxCopies) {
        final long copies = getCopiesPerTime(windowsPerTime, windowShifts);
        if (windowShifts == 0) {
            return "Each event would be in " + windowsPerTime + " windows, and the most allowed is "
                   + maxCopies + ". Use a larger advance or a smaller window";
        }
        return "Each event would be copied " + copies + " times, as it is in " + windowsPerTime
               + " windows and lag() and lead() read " + windowShifts + " other windows, and the most "
               + "allowed is " + maxCopies + ". Use a larger advance, a smaller window or fewer "
               + "different lag() and lead() distances";
    }

    /// Gets how many windows each time is in, which is one for tumbling windows and `size / slide` for
    /// hopping windows.
    ///
    /// @return The number of windows that cover any one time.
    public int getWindowsPerTime() {
        return windowsPerTime;
    }

    /// Gives the key of every window that covers a time, earliest window first. There are always
    /// [#getWindowsPerTime()] of them.
    ///
    /// @param timeMs   The time, in epoch milliseconds.
    /// @param consumer Receives the key of each window.
    public void forEachWindow(final long timeMs, final LongConsumer consumer) {
        final long latest = getLatestWindow(timeMs);
        for (int i = windowsPerTime - 1; i >= 0; i--) {
            consumer.accept(latest - i * slide);
        }
    }

    /// Gets the key of the latest window that has ended by a time, which is the window before the
    /// earliest of the windows that cover the time. No time in it or in any earlier window is after
    /// the time, so once events have arrived up to the time those windows are closed.
    ///
    /// @param timeMs The time, in epoch milliseconds.
    /// @return The key of the latest window that ends at or before the time.
    public long getLatestEndedWindow(final long timeMs) {
        return getLatestWindow(timeMs) - windowsPerTime * slide;
    }

    /// Gets the key of the window that starts next after another window.
    ///
    /// @param windowKey The key of a window.
    /// @return The key of the window one advance later, which for tumbling windows is one size later.
    public long getNextWindow(final long windowKey) {
        return windowKey + slide;
    }

    // The key of the latest of the windows that cover a time.
    private long getLatestWindow(final long timeMs) {
        return switch (alignment) {
            case FIXED -> {
                final long standardOffsetMillis = getStandardOffsetMillis(timeMs);
                yield floorToSlide(timeMs + standardOffsetMillis) - standardOffsetMillis;
            }
            case LOCAL_DAY -> floorToSlide(toLocalMillis(timeMs));
            case MONTH -> floorToSlideMonth(timeMs);
        };
    }

    /// Gets the key of the window that is a number of window sizes after, or before, another window.
    /// The result is the window that `lag` or `lead` reads.
    ///
    /// @param windowKey The key of a window, as given by [#forEachWindow(long, LongConsumer)].
    /// @param windows   How many window sizes to move, later if positive and earlier if negative.
    /// @return The key of that window.
    public long shift(final long windowKey, final int windows) {
        return windowKey + windows * size;
    }

    /// Gets the time that a window starts.
    ///
    /// @param windowKey The key of the window.
    /// @return The start of the window, in epoch milliseconds.
    public long getWindowStart(final long windowKey) {
        return switch (alignment) {
            case FIXED -> windowKey;
            case LOCAL_DAY -> toEpochMillis(windowKey);
            case MONTH -> monthWindowStart(windowKey);
        };
    }

    /// Gets the time that a window ends, which is the time that the window one size later starts.
    ///
    /// @param windowKey The key of the window.
    /// @return The end of the window, in epoch milliseconds.
    public long getWindowEnd(final long windowKey) {
        return getWindowStart(shift(windowKey, 1));
    }

    // The latest window start that is at or before a time, both on the same line of milliseconds.
    private long floorToSlide(final long millis) {
        return millis - Math.floorMod(millis - anchorMillis, slide);
    }

    // The key of the latest month window that starts at or before a time, which is the number of
    // months from January 1970 to the month that its start is counted from.
    private long floorToSlideMonth(final long timeMs) {
        final LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMs), zoneId);
        final LocalDateTime unmoved = monthOffsetEarlier
                ? SimpleDurationUtil.plus(local, monthOffset)
                : SimpleDurationUtil.minus(local, monthOffset);
        final long month = (unmoved.getYear() - EPOCH_YEAR) * MONTHS_PER_YEAR + unmoved.getMonthValue() - 1;
        return Math.floorDiv(month, slide) * slide;
    }

    private long monthWindowStart(final long month) {
        final LocalDateTime firstOfMonth = LocalDateTime.of(
                Math.toIntExact(Math.floorDiv(month, MONTHS_PER_YEAR) + EPOCH_YEAR),
                (int) Math.floorMod(month, MONTHS_PER_YEAR) + 1,
                1,
                0,
                0);
        final LocalDateTime moved = monthOffsetEarlier
                ? SimpleDurationUtil.minus(firstOfMonth, monthOffset)
                : SimpleDurationUtil.plus(firstOfMonth, monthOffset);
        return moved.atZone(zoneId).toInstant().toEpochMilli();
    }

    // The zone's offset from UTC with no daylight saving, which fixed windows are counted from so
    // that they keep their length when the clocks change.
    private long getStandardOffsetMillis(final long timeMs) {
        return fixedStandardOffsetMillis != null
                ? fixedStandardOffsetMillis
                : zoneId.getRules().getStandardOffset(Instant.ofEpochMilli(timeMs)).getTotalSeconds()
                  * MILLIS_PER_SECOND;
    }

    // The wall clock time of the zone, as milliseconds since 1 January 1970 on that clock. Every day
    // is 24 hours long on this line, whatever the clocks do.
    private long toLocalMillis(final long timeMs) {
        final ZoneOffset zoneOffset = zoneId.getRules().getOffset(Instant.ofEpochMilli(timeMs));
        return timeMs + zoneOffset.getTotalSeconds() * MILLIS_PER_SECOND;
    }

    // A wall clock time that the clocks skip is taken as the time that the clocks resume, and one
    // that comes twice is taken as the first of the two.
    private long toEpochMillis(final long localMillis) {
        final LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(
                Math.floorDiv(localMillis, MILLIS_PER_SECOND),
                (int) Math.floorMod(localMillis, MILLIS_PER_SECOND) * 1_000_000,
                ZoneOffset.UTC);
        final ZoneOffsetTransition transition = zoneId.getRules().getTransition(localDateTime);
        if (transition != null && transition.isGap()) {
            return transition.getInstant().toEpochMilli();
        }
        return localDateTime.atZone(zoneId).toInstant().toEpochMilli();
    }

    @Override
    public String toString() {
        return "TimeWindows{" +
               "alignment=" + alignment +
               ", zoneId=" + zoneId +
               ", size=" + size +
               ", slide=" + slide +
               ", anchorMillis=" + anchorMillis +
               ", monthOffset=" + monthOffset +
               ", monthOffsetEarlier=" + monthOffsetEarlier +
               '}';
    }

    // How windows of a unit are aligned.
    private enum Alignment {
        // Fixed lengths counted from the epoch.
        FIXED,
        // Whole days on the wall clock of the time zone.
        LOCAL_DAY,
        // Whole months of the calendar of the time zone.
        MONTH;

        private static Alignment of(final TimeUnit timeUnit) {
            return switch (timeUnit) {
                case NANOSECONDS -> throw new IllegalArgumentException(
                        "Windows can't be given in nanoseconds; the smallest unit is milliseconds");
                case MILLISECONDS, SECONDS, MINUTES, HOURS -> FIXED;
                case DAYS, WEEKS -> LOCAL_DAY;
                case MONTHS, YEARS -> MONTH;
            };
        }
    }
}
