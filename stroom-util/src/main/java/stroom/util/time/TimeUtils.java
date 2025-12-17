/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Objects;

public class TimeUtils {

    private static final Duration PERIOD_DURATION_THRESHOLD = Duration.ofDays(30);

    private TimeUtils() {
    }

    /**
     * @return The {@link Duration} between now and futureInstant.
     */
    public static Duration durationUntil(final Instant futureInstant) {
        return Duration.between(Instant.now(), futureInstant);
    }

    /**
     * @return The {@link Duration} between pastInstant and now.
     */
    public static Duration durationSince(final Instant pastInstant) {
        return Duration.between(pastInstant, Instant.now());
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     */
    public static Instant durationToThreshold(final StroomDuration stroomDuration) {
        return durationToThreshold(stroomDuration.getDuration());
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     *
     * @param now The instant used to represent the current time. Useful for testing purposes.
     */
    public static Instant durationToThreshold(final Instant now, final StroomDuration stroomDuration) {
        return durationToThreshold(now, stroomDuration.getDuration());
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     *
     * @param now The instant used to represent the current time. Useful for testing purposes.
     */
    public static Instant durationToThreshold(final Instant now, final Duration duration) {
        return now.minus(duration);
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     */
    public static Instant durationToThreshold(final Duration duration) {
        return durationToThreshold(Instant.now(), duration);
    }

    /**
     * If time is in the past then return the period from time until now().
     * If time is in the future then return the period from now() until time.
     */
    public static Period instantAsAge(final Instant time) {
        return instantAsAge(time, Instant.now());
    }

    /**
     * If time is in the past then return the period from time until now().
     * If time is in the future then return the period from now() until time.
     */
    public static Period instantAsAge(final Instant time, final Instant now) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(now);

        if (time.isBefore(now)) {
            return Period.between(
                    LocalDate.ofInstant(time, ZoneOffset.UTC),
                    LocalDate.ofInstant(now, ZoneOffset.UTC));
        } else {
            return Period.between(
                    LocalDate.ofInstant(now, ZoneOffset.UTC),
                    LocalDate.ofInstant(time, ZoneOffset.UTC));
        }
    }

    /**
     * If time is in the past then return the period from time until now().
     * If time is in the future then return the period from now() until time.
     */
    public static String instantAsAgeStr(final Instant time, final Instant now) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(now);

        return time.isBefore(now)
                ? toPeriodOrDurationStr(time, now)
                : toPeriodOrDurationStr(now, time);
    }

    public static String periodAsAgeStr(final TimePeriod timePeriod) {
        Objects.requireNonNull(timePeriod);
        return toPeriodOrDurationStr(timePeriod.getFrom(), timePeriod.getTo());
    }

    public static String toPeriodOrDurationStr(final Instant from, final Instant to) {
        final Duration duration = Duration.between(from, to);
        return duration.compareTo(PERIOD_DURATION_THRESHOLD) < 0
                ? duration.toString()
                : Period.between(
                        LocalDate.ofInstant(from, ZoneOffset.UTC),
                        LocalDate.ofInstant(to, ZoneOffset.UTC)).toString();
    }

    /**
     * @return True if {@code duration1 >= duration2}
     */
    public static boolean isGreaterThanOrEqualTo(final Duration duration1, final Duration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) >= 0;
    }

    /**
     * @return True if {@code duration1 >= duration2}
     */
    public static boolean isGreaterThanOrEqualTo(final StroomDuration duration1, final StroomDuration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) >= 0;
    }

    /**
     * @return True if {@code duration1 > duration2}
     */
    public static boolean isGreaterThan(final Duration duration1, final Duration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) > 0;
    }

    /**
     * @return True if {@code duration1 > duration2}
     */
    public static boolean isGreaterThan(final StroomDuration duration1, final StroomDuration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) > 0;
    }

    /**
     * @return True if {@code duration1 <= duration2}
     */
    public static boolean isLessThanOrEqualTo(final Duration duration1, final Duration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) <= 0;
    }

    /**
     * @return True if {@code duration1 <= duration2}
     */
    public static boolean isLessThanOrEqualTo(final StroomDuration duration1, final StroomDuration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) <= 0;
    }

    /**
     * @return True if {@code duration1 < duration2}
     */
    public static boolean isLessThan(final Duration duration1, final Duration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) < 0;
    }

    /**
     * @return True if {@code duration1 < duration2}
     */
    public static boolean isLessThan(final StroomDuration duration1, final StroomDuration duration2) {
        return Objects.requireNonNull(duration1).compareTo(Objects.requireNonNull(duration2)) < 0;
    }
}
