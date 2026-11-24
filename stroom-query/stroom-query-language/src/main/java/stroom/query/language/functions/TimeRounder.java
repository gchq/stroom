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

import stroom.query.language.token.TimeLiteral;
import stroom.util.shared.time.SimpleDuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/// Moves times onto multiples of a duration or period.
///
/// The multiples of a date are counted on the local line of a time zone, from an origin, so a day starts at
/// local midnight. When clocks go forward, a multiple in the time that is skipped is the moment of the change.
/// When they go back, a multiple in the time that happens twice is a multiple both times if the duration is short
/// enough to have one in each pass, such as 15 minutes, and otherwise only the first time, so that a day that has
/// two midnights is still one day.
///
/// The multiples of a duration are counted from zero.
final class TimeRounder {

    private static final LocalDateTime EPOCH = LocalDate.EPOCH.atStartOfDay();
    // The first Monday after the epoch, which is where the weeks of a window clause are counted from too.
    private static final LocalDateTime FIRST_MONDAY = LocalDate.of(1970, 1, 5).atStartOfDay();
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("[+-]?(?:\\d++(?:\\.\\d*+)?|\\.\\d++)(?:[eE][+-]?\\d{1,3})?");
    private static final int DAYS_PER_WEEK = 7;
    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    private final Mode mode;
    // The duration in milliseconds, or zero if this rounds to a period.
    private final long durationMillis;
    // The period in months or in days. Only one of these and the duration is not zero.
    private final long months;
    private final long days;
    // Null to count from the default origin.
    private final Origin origin;

    private TimeRounder(final Mode mode,
                        final long durationMillis,
                        final long months,
                        final long days,
                        final Origin origin) {
        this.mode = mode;
        this.durationMillis = durationMillis;
        this.months = months;
        this.days = days;
        this.origin = origin;
    }

    /// Creates a rounder that rounds to multiples of a duration.
    ///
    /// @param mode   Which multiple to round to.
    /// @param millis The duration in milliseconds.
    /// @param origin The time to count multiples from, or `null` for local midnight on 1 January 1970.
    /// @return The rounder.
    /// @throws IllegalArgumentException If the duration is not at least a millisecond.
    static TimeRounder ofDuration(final Mode mode, final long millis, final Origin origin) {
        if (millis <= 0) {
            throw new IllegalArgumentException("Can't round to " + ValDuration.create(millis)
                                               + " - it must be at least one millisecond");
        }
        return new TimeRounder(mode, millis, 0, 0, origin);
    }

    /// Creates a rounder that rounds to multiples of a period.
    ///
    /// @param mode   Which multiple to round to.
    /// @param period The period, which can only have one of years, months and days.
    /// @param origin The time to count multiples from. If it is `null` then months and years are counted from
    ///               January 1970, whole weeks from Monday 5 January 1970 and other days from 1 January 1970.
    /// @return The rounder.
    /// @throws IllegalArgumentException If the period is not more than zero of one unit.
    static TimeRounder ofPeriod(final Mode mode, final Period period, final Origin origin) {
        int fields = 0;
        for (final int field : new int[]{period.getYears(), period.getMonths(), period.getDays()}) {
            if (field < 0) {
                throw new IllegalArgumentException("Can't round to " + period + " - it must be more than zero");
            } else if (field > 0) {
                fields++;
            }
        }
        if (fields == 0) {
            throw new IllegalArgumentException("Can't round to " + period + " - it must be more than zero");
        } else if (fields > 1) {
            throw new IllegalArgumentException("Can't round to " + period
                                               + " - it must only have one of years, months and days");
        }
        return new TimeRounder(mode, 0, period.toTotalMonths(), period.getDays(), origin);
    }

    /// Reads a duration or period to round to from text, such as `15m`, `PT15M`, `3M` or `P3M`.
    ///
    /// @param text The text.
    /// @return A duration or period.
    /// @throws DateTimeException   If the text is not a duration or period.
    /// @throws ArithmeticException If it is too long.
    static Val parseMultiple(final String text) {
        final String trimmed = text.trim();
        final String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("P") || upper.startsWith("-P")) {
            return upper.contains("T")
                    ? ValDuration.create(Duration.parse(trimmed))
                    : ValPeriod.create(Period.parse(trimmed));
        }
        final List<SimpleDuration> parts = TimeLiteral.parseCompound(trimmed);
        if (parts.stream().allMatch(TimeLiteral::isPeriod)) {
            Period period = Period.ZERO;
            for (final SimpleDuration part : parts) {
                period = period.plus(TimeLiteral.toPeriod(part));
            }
            return ValPeriod.create(period);
        }
        return ValDuration.create(TimeLiteral.parseCompoundMillis(trimmed));
    }

    /// Rounds a value.
    ///
    /// @param val  A duration, or a date or any value that can be used as one.
    /// @param zone The time zone to count the multiples of a date in.
    /// @return The rounded value, a null if the value can't be used as a date, or an error.
    Val round(final Val val, final ZoneId zone) {
        try {
            return switch (val) {
                case final ValPeriod ignored -> ValErr.create("Can't round a period");
                case final ValDuration duration -> ValDuration.create(roundDuration(duration.toLong()));
                case final ValString text -> roundText(text.toString().trim(), zone);
                default -> {
                    final Long millis = val.toLong();
                    yield millis == null
                            ? ValNull.INSTANCE
                            : roundDate(millis, zone);
                }
            };
        } catch (final RuntimeException e) {
            return ValErr.create(e.getMessage());
        }
    }

    private Val roundDate(final long millis, final ZoneId zone) {
        return ValDate.create(round(Instant.ofEpochMilli(millis).atZone(zone)).toInstant().toEpochMilli());
    }

    // Text can be a date, a number of milliseconds since the epoch or a duration.
    private Val roundText(final String text, final ZoneId zone) {
        if (text.isEmpty()) {
            return ValNull.INSTANCE;
        }
        try {
            return roundDate(DateUtil.parse(text, DateUtil.DEFAULT_ISO_PARSER, zone), zone);
        } catch (final DateTimeParseException e) {
            // Not a date.
        }
        if (isNumber(text)) {
            return roundDate(parseMillis(text), zone);
        }
        final long millis;
        try {
            millis = ValDurationUtil.parseToMilliseconds(text);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("'" + text + "' is not a date or a duration", e);
        }
        return ValDuration.create(roundDuration(millis));
    }

    /// Tells whether text is a plain number, with no unit.
    ///
    /// @param text The text.
    /// @return True if it is.
    static boolean isNumber(final String text) {
        return NUMBER_PATTERN.matcher(text).matches();
    }

    /// Reads a number of milliseconds from text that is a plain number. Any part of a millisecond is dropped.
    ///
    /// @param text The text.
    /// @return The milliseconds.
    /// @throws ArithmeticException If the number is too large.
    static long parseMillis(final String text) {
        return new BigDecimal(text).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    private long roundDuration(final long millis) {
        if (origin != null) {
            throw new IllegalArgumentException("An origin can only be used to round a date");
        }
        final long multiple;
        if (durationMillis > 0) {
            multiple = durationMillis;
        } else if (months == 0) {
            multiple = Duration.ofDays(days).toMillis();
        } else {
            throw new IllegalArgumentException("Can't round a duration by a period of months or years");
        }
        final long remainder = Math.floorMod(millis, multiple);
        final long floor = Math.subtractExact(millis, remainder);
        if (remainder == 0 || Mode.FLOOR.equals(mode)
            || (Mode.ROUND.equals(mode) && remainder < multiple - remainder)) {
            return floor;
        }
        return Math.addExact(floor, multiple);
    }

    /// Rounds a time, counting multiples in its own time zone.
    ///
    /// @param time The time.
    /// @return The rounded time.
    ZonedDateTime round(final ZonedDateTime time) {
        final ZoneId zone = time.getZone();
        final Instant instant = time.toInstant();
        final Multiples multiples = durationMillis > 0 && durationMillis < MILLIS_PER_DAY
                ? new ShortMultiples(zone, toLocalMillis(getOrigin(zone)), durationMillis)
                : new LongMultiples(zone, getOrigin(zone));
        final Instant floor = multiples.floor(instant);
        if (floor.equals(instant) || Mode.FLOOR.equals(mode)) {
            return floor.atZone(zone);
        }
        final ZonedDateTime ceiling = multiples.next(instant).atZone(zone);
        return Mode.CEILING.equals(mode)
                ? ceiling
                : nearest(time, floor.atZone(zone), ceiling);
    }

    /// Gets the nearer of the times either side of a time, by the exact time between them. The later is used if
    /// they are as near as each other.
    ///
    /// @param time    The time.
    /// @param floor   The time at or before it.
    /// @param ceiling The time after it.
    /// @return The nearer time.
    static ZonedDateTime nearest(final ZonedDateTime time,
                                 final ZonedDateTime floor,
                                 final ZonedDateTime ceiling) {
        return Duration.between(floor, time).compareTo(Duration.between(time, ceiling)) < 0
                ? floor
                : ceiling;
    }

    private LocalDateTime getOrigin(final ZoneId zone) {
        if (origin != null) {
            return origin.toLocal(zone);
        }
        return days > 0 && days % DAYS_PER_WEEK == 0
                ? FIRST_MONDAY
                : EPOCH;
    }

    // Local times are measured as if the zone were UTC, so that multiples of an hour fall on the local hour.
    private static long toLocalMillis(final LocalDateTime local) {
        return local.toInstant(ZoneOffset.UTC).toEpochMilli();
    }


    // --------------------------------------------------------------------------------


    /// Which multiple a time is rounded to.
    enum Mode {
        /// The multiple at or before the time.
        FLOOR,
        /// The multiple at or after the time.
        CEILING,
        /// The nearest multiple, or the later if two are as near as each other.
        ROUND
    }


    // --------------------------------------------------------------------------------


    /// The local time that multiples are counted from.
    interface Origin {

        /// Gets the origin in a time zone.
        ///
        /// @param zone The time zone.
        /// @return The local time.
        LocalDateTime toLocal(ZoneId zone);
    }


    // --------------------------------------------------------------------------------


    private interface Multiples {

        // The last multiple at or before a time.
        Instant floor(Instant time);

        // The first multiple after a time.
        Instant next(Instant time);
    }


    // --------------------------------------------------------------------------------


    // Multiples of a period, or of a duration of at least a day. These are longer than any change of the clocks.
    private final class LongMultiples implements Multiples {

        private final ZoneId zone;
        private final LocalDateTime start;

        LongMultiples(final ZoneId zone, final LocalDateTime start) {
            this.zone = zone;
            this.start = start;
        }

        @Override
        public Instant floor(final Instant time) {
            return toInstant(floorUnits(time));
        }

        @Override
        public Instant next(final Instant time) {
            long units = floorUnits(time) + 1;
            // Two multiples are at the same time if a change of the clocks skips all of the time between them.
            while (!toInstant(units).isAfter(time)) {
                units++;
            }
            return toInstant(units);
        }

        // The number of whole units from the origin to the last multiple that isn't after a time.
        private long floorUnits(final Instant time) {
            final LocalDateTime local = time.atZone(zone).toLocalDateTime();
            long units;
            if (months > 0) {
                units = Math.floorDiv(ChronoUnit.MONTHS.between(start, local), months);
            } else if (days > 0) {
                units = Math.floorDiv(ChronoUnit.DAYS.between(start, local), days);
            } else {
                units = Math.floorDiv(ChronoUnit.MILLIS.between(start, local), durationMillis);
            }
            // This can be a unit out for a time before the origin, as whole units are counted towards the origin,
            // when a month is too short for the day of the month that the origin is on, or if the clocks change.
            while (toInstant(units).isAfter(time)) {
                units--;
            }
            while (!toInstant(units + 1).isAfter(time)) {
                units++;
            }
            return units;
        }

        // Counted from the origin so that months which start on the 31st go back to it after a shorter month.
        private Instant toInstant(final long units) {
            final LocalDateTime local;
            if (months > 0) {
                local = start.plusMonths(Math.multiplyExact(units, months));
            } else if (days > 0) {
                local = start.plusDays(Math.multiplyExact(units, days));
            } else {
                local = start.plus(Math.multiplyExact(units, durationMillis), ChronoUnit.MILLIS);
            }
            final ZoneOffsetTransition transition = zone.getRules().getTransition(local);
            return transition != null && transition.isGap()
                    ? transition.getInstant()
                    // The first of the two times if it happens twice.
                    : local.atZone(zone).toInstant();
        }
    }


    // --------------------------------------------------------------------------------


    // Multiples of a duration of less than a day, worked out between one change of the clocks and the next.
    private static final class ShortMultiples implements Multiples {

        private final ZoneId zone;
        private final ZoneRules rules;
        private final long originMillis;
        private final long durationMillis;

        ShortMultiples(final ZoneId zone, final long originMillis, final long durationMillis) {
            this.zone = zone;
            this.rules = zone.getRules();
            this.originMillis = originMillis;
            this.durationMillis = durationMillis;
        }

        @Override
        public Instant floor(final Instant time) {
            final Instant floor = time.minusMillis(remainder(time));
            // The last change of the clocks at or before the time.
            final ZoneOffsetTransition transition = rules.previousTransition(time.plusNanos(1));
            if (transition == null || (!floor.isBefore(transition.getInstant()) && !isRepeat(floor, transition))) {
                return floor;
            }
            if (hasMultipleInGap(transition)) {
                return transition.getInstant();
            }
            return floor(transition.getInstant().minusMillis(1));
        }

        @Override
        public Instant next(final Instant time) {
            Instant next = time.plusMillis(durationMillis - remainder(time));
            final ZoneOffsetTransition previous = rules.previousTransition(time.plusNanos(1));
            if (previous != null && isRepeat(next, previous)) {
                next = next.plusMillis(durationMillis);
            }
            final ZoneOffsetTransition transition = rules.nextTransition(time);
            if (transition == null || next.isBefore(transition.getInstant())) {
                return next;
            }
            return floor(transition.getInstant()).equals(transition.getInstant())
                    ? transition.getInstant()
                    : next(transition.getInstant());
        }

        // How far a time is past the local multiple before it.
        private long remainder(final Instant time) {
            // Java's % would give a negative remainder for a time before the origin, which moves it the wrong way.
            return Math.floorMod(toLocalMillis(time.atZone(zone).toLocalDateTime()) - originMillis, durationMillis);
        }

        // Whether a time is in the second pass of time that happens twice, and the duration is too long to have a
        // multiple in each pass.
        private boolean isRepeat(final Instant time, final ZoneOffsetTransition transition) {
            return transition.isOverlap()
                   && durationMillis > transition.getDuration().abs().toMillis()
                   && time.atOffset(transition.getOffsetAfter())
                           .toLocalDateTime()
                           .isBefore(transition.getDateTimeBefore());
        }

        private boolean hasMultipleInGap(final ZoneOffsetTransition transition) {
            if (!transition.isGap()) {
                return false;
            }
            final long before = toLocalMillis(transition.getDateTimeBefore());
            final long after = toLocalMillis(transition.getDateTimeAfter());
            return before + Math.floorMod(originMillis - before, durationMillis) < after;
        }
    }
}
