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

package stroom.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Utility class work working with Period objects. Here partly due to GWT not
 * wanting to import all of apache commons.
 */
public final class PeriodUtil {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private PeriodUtil() {
        // NA for this utility
    }

    /**
     * Create a period up to a date with a number of day offset.
     */
    public static Period createToDateWithOffset(final long to, final int days) {
        final ZonedDateTime dateTime = Instant.ofEpochMilli(to).atZone(UTC);
        final long millis = dateTime.plusDays(days).toInstant().toEpochMilli();
        return new Period(null, millis);
    }

    /**
     * Create a year period.
     *
     * @param year e.g. 2001
     */
    public static Period createYearPeriod(final Integer year) {
        final ZonedDateTime dateTime = LocalDate.of(year, 1, 1).atStartOfDay(UTC);
        final long from = dateTime.toInstant().toEpochMilli();
        final long to = dateTime.plusYears(1).toInstant().toEpochMilli();
        return new Period(from, to);
    }

    /**
     * Create a month period.
     *
     * @param year e.g. 2001
     */
    public static Period createYearMonthPeriod(final Integer year, final Integer month) {
        final ZonedDateTime dateTime = LocalDate.of(year, month, 1).atStartOfDay(UTC);
        final long from = dateTime.toInstant().toEpochMilli();
        final long to = dateTime.plusMonths(1).toInstant().toEpochMilli();
        return new Period(from, to);
    }

    /**
     * Create a day period.
     *
     * @param year  e.g. 2001
     * @param month e.g. 01=jan
     * @param day   e.g. 31
     */
    public static Period createYearMonthDayPeriod(final Integer year, final Integer month, final Integer day) {
        final ZonedDateTime dateTime = LocalDate.of(year, month, day).atStartOfDay(UTC);
        final long from = dateTime.toInstant().toEpochMilli();
        final long to = dateTime.plusDays(1).toInstant().toEpochMilli();
        return new Period(from, to);
    }

    /**
     * Create a date in the same way.
     *
     * @param year  e.g. 2001
     * @param month e.g. 01
     * @param day   e.g. 31
     */
    public static long createDate(final Integer year, final Integer month, final Integer day) {
        final ZonedDateTime dateTime = LocalDate.of(year, month, day).atStartOfDay(UTC);
        final long millis = dateTime.toInstant().toEpochMilli();
        return millis;
    }

    public static int getPrecision(final long duration, final int pointsRequired) {
        final double durationLog = Math.log10(duration);
        final double pointLog = Math.log10(pointsRequired);
        return (int) (durationLog - pointLog);
    }
}
