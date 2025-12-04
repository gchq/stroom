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

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDurationUtil {

    private static final Pattern SIMPLE_FORMAT_PATTERN = Pattern.compile("^(\\d+)(\\w+)$");

    public static SimpleDuration parse(final String string) throws ParseException {
        Objects.requireNonNull(string, "String is null");

        final String trimmed = string.trim();
        final Matcher matcher = SIMPLE_FORMAT_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            try {
                final String number = matcher.group(1);
                final String unit = matcher.group(2);
                final long time = Long.parseLong(number);
                final TimeUnit timeUnit = switch (unit) {
                    case "ns" -> TimeUnit.NANOSECONDS;
                    case "ms" -> TimeUnit.MILLISECONDS;
                    case "s" -> TimeUnit.SECONDS;
                    case "m" -> TimeUnit.MINUTES;
                    case "h" -> TimeUnit.HOURS;
                    case "d", "D" -> TimeUnit.DAYS;
                    case "w", "W" -> TimeUnit.WEEKS;
                    case "M" -> TimeUnit.MONTHS;
                    case "y", "Y" -> TimeUnit.YEARS;
                    default -> throw new ParseException("Time unit is invalid: " + trimmed, 0);
                };

                return SimpleDuration.builder().time(time).timeUnit(timeUnit).build();
            } catch (final RuntimeException e) {
                throw new ParseException("Format of duration string is invalid: " + trimmed, 0);
            }
        } else {
            throw new ParseException("Format of duration string is invalid: " + trimmed, 0);
        }
    }

    public static Instant plus(final Instant dateTime,
                               final SimpleDuration simpleDuration) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime, ZoneOffset.UTC);
        localDateTime = plus(localDateTime, simpleDuration);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime plus(final LocalDateTime dateTime,
                                     final SimpleDuration simpleDuration) {
        if (simpleDuration == null) {
            return dateTime;
        }
        return switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS -> dateTime.plusNanos(simpleDuration.getTime());
            case MILLISECONDS -> dateTime.plusNanos(simpleDuration.getTime() * 1_000_000);
            case SECONDS -> dateTime.plusSeconds(simpleDuration.getTime());
            case MINUTES -> dateTime.plusMinutes(simpleDuration.getTime());
            case HOURS -> dateTime.plusHours(simpleDuration.getTime());
            case DAYS -> dateTime.plusDays(simpleDuration.getTime());
            case WEEKS -> dateTime.plusWeeks(simpleDuration.getTime());
            case MONTHS -> dateTime.plusMonths(simpleDuration.getTime());
            case YEARS -> dateTime.plusYears(simpleDuration.getTime());
        };
    }

    public static Instant minus(final Instant dateTime,
                                final SimpleDuration simpleDuration) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime, ZoneOffset.UTC);
        localDateTime = minus(localDateTime, simpleDuration);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime minus(final LocalDateTime dateTime,
                                      final SimpleDuration simpleDuration) {
        if (simpleDuration == null) {
            return dateTime;
        }
        return switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS -> dateTime.minusNanos(simpleDuration.getTime());
            case MILLISECONDS -> dateTime.minusNanos(simpleDuration.getTime() * 1_000_000);
            case SECONDS -> dateTime.minusSeconds(simpleDuration.getTime());
            case MINUTES -> dateTime.minusMinutes(simpleDuration.getTime());
            case HOURS -> dateTime.minusHours(simpleDuration.getTime());
            case DAYS -> dateTime.minusDays(simpleDuration.getTime());
            case WEEKS -> dateTime.minusWeeks(simpleDuration.getTime());
            case MONTHS -> dateTime.minusMonths(simpleDuration.getTime());
            case YEARS -> dateTime.minusYears(simpleDuration.getTime());
        };
    }

    public static LocalDateTime roundDown(final LocalDateTime dateTime, final SimpleDuration simpleDuration) {
        return switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS -> LocalDateTime.of(
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    dateTime.getHour(),
                    dateTime.getMinute(),
                    dateTime.getSecond(),
                    (dateTime.getNano() / 1_000_000) & 1_000_000);
            case MILLISECONDS -> LocalDateTime.of(
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    dateTime.getHour(),
                    dateTime.getMinute(),
                    dateTime.getSecond());
            case SECONDS -> LocalDateTime.of(
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    dateTime.getHour(),
                    dateTime.getMinute());
            case MINUTES -> LocalDateTime.of(
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    dateTime.getHour(),
                    0);
            case HOURS -> LocalDateTime.of(
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    0,
                    0);
            case DAYS -> LocalDateTime.of(
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    1,
                    0,
                    0);
            case WEEKS, MONTHS -> LocalDateTime.of(
                    dateTime.getYear(),
                    1,
                    1,
                    0,
                    0);
            case YEARS -> LocalDateTime.of(
                    1970,
                    1,
                    1,
                    0,
                    0);
        };
    }
}
