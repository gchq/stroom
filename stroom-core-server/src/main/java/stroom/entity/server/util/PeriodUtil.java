/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.server.util;

import java.util.Locale;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import stroom.entity.shared.Period;

/**
 * Utility class work working with Period objects. Here partly due to GWT not
 * wanting to import all of apache commons.
 */
public final class PeriodUtil {
    static {
        // Set the default timezone and locale for all date time operations.
        DateTimeZone.setDefault(DateTimeZone.UTC);
        Locale.setDefault(Locale.ROOT);
    }

    private PeriodUtil() {
        // NA for this utility
    }

    /**
     * Create a period up to a date with a number of day offset.
     */
    public static Period createToDateWithOffset(final long to, final int days) {
        return new Period(null, new DateTime(to).plusDays(days).getMillis());
    }

    /**
     * Create a year period.
     *
     * @param year
     *            e.g. 2001
     */
    public static Period createYearPeriod(final Integer year) {
        final DateTime startOfYear = new DateTime(year, 1, 1, 0, 0, 0, 0);
        return new Period(startOfYear.getMillis(), startOfYear.plusYears(1).getMillis());
    }

    /**
     * Create a month period.
     *
     * @param year
     *            e.g. 2001
     */
    public static Period createYearMonthPeriod(final Integer year, final Integer month) {
        final DateTime startOfMonth = new DateTime(year, month, 1, 0, 0, 0, 0);
        return new Period(startOfMonth.getMillis(), startOfMonth.plusMonths(1).getMillis());
    }

    /**
     * Create a day period.
     *
     * @param year
     *            e.g. 2001
     * @param month
     *            e.g. 01=jan
     * @param day
     *            e.g. 31
     */
    public static Period createYearMonthDayPeriod(final Integer year, final Integer month, final Integer day) {
        final DateTime startOfDay = new DateTime(year, month, day, 0, 0, 0, 0);
        return new Period(startOfDay.getMillis(), startOfDay.plusDays(1).getMillis());
    }

    /**
     * Create a date in the same way.
     *
     * @param year
     *            e.g. 2001
     * @param month
     *            e.g. 01
     * @param day
     *            e.g. 31
     */
    public static long createDate(final Integer year, final Integer month, final Integer day) {
        return new DateTime(year, month, day, 0, 0, 0, 0).getMillis();
    }

    public static int getPrecision(final long duration, int pointsRequired) {
        final double durationLog = Math.log10(duration);
        final double pointLog = Math.log10(pointsRequired);
        return (int) (durationLog - pointLog);
    }
}
