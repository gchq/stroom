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

import stroom.util.shared.Range;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * <p>
 * Class that represents a period. May have null upper or lower bounds to
 * indicate before or after a time rather than between 2 times.
 * </p>
 * <p>
 * <p>
 * The upper bound is not part of the period. E.g. 1/1/2001 to 1/1/2002 does not
 * include 1/1/2002.
 * </p>
 */
public class Period extends Range<Long> {

    private static final int N3 = 3;

    private static final long serialVersionUID = -7978314234943698113L;
    private static final long MS_IN_SECOND = 1000;
    private static final long MS_IN_MINUTE = 1000 * 60;
    private static final long MS_IN_HOUR = 1000 * 60 * 60;
    private static final long MS_IN_DAY = 1000 * 60 * 60 * 24;

    public Period() {
    }

    public Period(final Long fromMs, final Long toMs) {
        super(fromMs, toMs);
    }

    public Period(final Instant from, final Instant to) {
        super(from.toEpochMilli(), to.toEpochMilli());
    }

    public static final Period clone(final Period period) {
        if (period == null) {
            return null;
        }
        return new Period(period.getFromMs(), period.getToMs());
    }

    public static Comparator<Period> comparingByFromTime() {
        return Comparator.comparing(Period::getFromMs);
    }

    public static Comparator<Period> comparingByToTime() {
        return Comparator.comparing(Period::getFromMs);
    }

    /**
     * Create a period just covering 1 ms.
     */
    public static final Period createMsPeriod(final long fromMs) {
        return new Period(fromMs, fromMs + 1);
    }

    /**
     * Create a period just covering 1 ms.
     */
    public static final Period createNullPeriod() {
        final Period period = new Period();
        period.setMatchNull(true);
        return period;
    }

    private static String zeroPad(final int amount, final String in) {
        final int left = amount - in.length();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < left; i++) {
            out.append("0");
        }
        out.append(in);
        return out.toString();
    }

    public Period doublePeriod() {
        if (!isBounded()) {
            throw new RuntimeException("Cannot double unbounded period.");
        }

        final long duration = getTo() - getFrom();
        final long half = duration / 2;
        return new Period(getFrom() - half, getTo() + half);
    }

    /**
     * @return whole hours in this period
     */
    public Long getHoursInPeriod() {
        if (isBounded()) {
            final long duration = getTo() - getFrom();
            return duration / MS_IN_HOUR;
        }
        return null;
    }

    public int getPrecision(final int pointsRequired) {
        if (!isBounded()) {
            return 0;
        }
        if (pointsRequired == 0) {
            return 0;
        }
        final long duration = getTo() - getFrom();

        final long scale = duration / pointsRequired;

        return (int) Math.log10(scale);
    }

    /**
     * @return whole days in period
     */
    public Long getDaysInPeriod() {
        if (isBounded()) {
            final long duration = getTo() - getFrom();
            final long days = (duration / MS_IN_DAY);
            return days;

        }
        return null;
    }

    /**
     * Inclusive
     */
    // Here for XML serialisation.
    public Long getFromMs() {
        return super.getFrom();
    }

    /**
     * Inclusive
     */
    public Optional<Instant> getFromTime() {
        return Optional.ofNullable(super.getFrom())
                .map(Instant::ofEpochMilli);
    }

    // Here for XML serialisation.
    /**
     * Inclusive
     */
    public void setFromMs(final Long from) {
        super.setFrom(from);
    }

    // Here for XML serialisation.
    /**
     * Exclusive
     */
    public Long getToMs() {
        return super.getTo();
    }

    /**
     * Exclusive
     */
    public Optional<Instant> getToTime() {
        return Optional.ofNullable(super.getTo())
                .map(Instant::ofEpochMilli);
    }

    // Here for XML serialisation.
    /**
     * Exclusive
     */
    public void setToMs(final Long to) {
        super.setTo(to);
    }

    /**
     * Gets the duration as a user readable string.
     */
    public String getDurationStr() {
        if (!isBounded()) {
            return null;
        }
        final Duration duration = Duration.between(
                Instant.ofEpochMilli(getFrom()), Instant.ofEpochMilli(getTo()));

        long durationMs = getTo() - getFrom();
        final int totalHours = (int) (durationMs / MS_IN_HOUR);
        durationMs = durationMs - (totalHours * MS_IN_HOUR);

        final int hours;
        final int days;
        if (totalHours > 24) {
            hours = totalHours % 24;
            days = totalHours / 24;
        } else {
            hours = totalHours;
            days = 0;
        }

        final int minutes = (int) (durationMs / MS_IN_MINUTE);
        durationMs = durationMs - (minutes * MS_IN_MINUTE);
        final int seconds = (int) (durationMs / MS_IN_SECOND);
        durationMs = durationMs - (seconds * MS_IN_SECOND);

        final StringBuilder sb = new StringBuilder();
//        sb.append(zeroPad(2, Integer.toString(totalHours)));
//        sb.append(":");
//        sb.append(zeroPad(2, Integer.toString(minutes)));
//        sb.append(":");
//        sb.append(zeroPad(2, Integer.toString(seconds)));
//        sb.append(".");
//        sb.append(zeroPad(N3, Long.toString(duration)));

        sb.append(duration);

        if (days > 0) {
            sb.append(" (");
            sb.append(days);
            sb.append(" days ");
            sb.append(hours);
            sb.append(" hrs)");
        }

        return sb.toString();
    }

    public Long duration() {
        if (getFrom() != null && getTo() != null) {
            return getTo() - getFrom();
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("From: ");
        if (getFrom() == null) {
            builder.append("null");
        } else {
            builder.append(Instant.ofEpochMilli(getFrom()));
        }
        builder.append(", To: ");
        if (getTo() == null) {
            builder.append("null");
        } else {
            builder.append(Instant.ofEpochMilli(getTo()));
        }
        builder.append(", Duration: ");
        builder.append(getDurationStr());
        builder.append("]");
        return builder.toString();
    }
}
