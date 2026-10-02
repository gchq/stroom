/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.data.archive;

import stroom.planb.shared.BucketGranularity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Server-side time-bucketing logic for {@link BucketGranularity}.
 *
 * <p>{@link BucketGranularity} itself lives in the {@code stroom-core-shared}
 * module which is GWT-compiled, so it cannot reference {@code java.time.*}.
 * This utility holds all the time-dependent methods and is only used on the
 * server side (impl module).
 */
public final class BucketGranularityUtil {

    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH");
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    private BucketGranularityUtil() {
    }

    /**
     * Returns the directory-label for the archive shard that covers
     * the given instant.
     */
    public static String label(final BucketGranularity granularity,
                               final Instant instant) {
        final ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        return switch (granularity) {
            case HOUR -> HOUR_FMT.format(zdt);
            case DAY  -> DAY_FMT.format(zdt);
            case WEEK -> {
                final LocalDate date = zdt.toLocalDate();
                final LocalDate monday = date.with(DayOfWeek.MONDAY);
                yield "week-" + DAY_FMT.format(monday); // e.g. week-2025-05-12
            }
        };
    }

    /**
     * Returns the exclusive end of the time bucket identified by {@code label},
     * i.e. the first instant NOT covered by this archive shard.
     * Returns {@code null} if {@code label} cannot be parsed.
     */
    public static Instant bucketEnd(final BucketGranularity granularity,
                                    final String label) {
        try {
            return switch (granularity) {
                case HOUR -> {
                    // "2025-05-18_14" -> end is 2025-05-18T15:00Z
                    final String[] parts = label.split("_");
                    final LocalDate date = LocalDate.parse(parts[0], DAY_FMT);
                    final int hour = Integer.parseInt(parts[1]);
                    yield date.atTime(hour, 0)
                            .plusHours(1)
                            .atZone(ZoneOffset.UTC)
                            .toInstant();
                }
                case DAY -> {
                    // "2025-05-18" -> end is 2025-05-19T00:00Z
                    final LocalDate date = LocalDate.parse(label, DAY_FMT);
                    yield date.plusDays(1)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant();
                }
                case WEEK -> {
                    // "week-2025-05-12" -> Monday 2025-05-12 + 7 days
                    final String dateStr = label.substring("week-".length());
                    final LocalDate monday = LocalDate.parse(dateStr, DAY_FMT);
                    yield monday.plusWeeks(1)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant();
                }
            };
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Returns the inclusive start of the time bucket identified by {@code label},
     * i.e. the first instant covered by this archive shard.
     * Returns {@code null} if {@code label} cannot be parsed.
     */
    public static Instant bucketStart(final BucketGranularity granularity,
                                       final String label) {
        final Instant end = bucketEnd(granularity, label);
        if (end == null) {
            return null;
        }
        return switch (granularity) {
            case HOUR -> end.minus(1, ChronoUnit.HOURS);
            case DAY  -> end.minus(1, ChronoUnit.DAYS);
            case WEEK -> end.minus(7, ChronoUnit.DAYS);
        };
    }

    /**
     * Returns {@code true} if the half-open bucket {@code [bucketStart, bucketEnd)}
     * overlaps the closed filter interval {@code [filterFromMs, filterToMs]}.
     */
    public static boolean overlaps(final Instant bucketStart,
                                    final Instant bucketEnd,
                                    final long filterFromMs,
                                    final long filterToMs) {
        return bucketStart.toEpochMilli() <= filterToMs
                && bucketEnd.toEpochMilli() > filterFromMs;
    }

    /**
     * Detects the granularity from a bucket's directory name, which is the only record of how that
     * bucket was written — a doc whose configured granularity has since changed still has buckets in
     * the old layout. Used by both readers and bucket expiry for that reason.
     *
     * @return the matching granularity, or {@code null} if unrecognised
     */
    public static BucketGranularity detect(final String label) {
        if (label == null) {
            return null;
        }
        if (label.startsWith("week-")) {
            return BucketGranularity.WEEK;
        }
        if (label.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}")) {
            return BucketGranularity.HOUR;
        }
        if (label.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return BucketGranularity.DAY;
        }
        return null;
    }
}
