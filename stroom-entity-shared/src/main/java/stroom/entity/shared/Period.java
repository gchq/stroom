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

package stroom.entity.shared;

import java.util.Date;

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
        // Default constructor necessary for GWT serialisation.
    }

    public Period(final Long fromMs, final Long toMs) {
        super(fromMs, toMs);
    }

    public static final Period clone(Period period) {
        if (period == null) {
            return null;
        }
        return new Period(period.getFromMs(), period.getToMs());
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
        Period period = new Period();
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

        long duration = getTo() - getFrom();
        long half = duration / 2;
        return new Period(getFrom() - half, getTo() + half);
    }

    /**
     * @return whole hours in this period
     */
    public Long getHoursInPeriod() {
        if (isBounded()) {
            long duration = getTo() - getFrom();
            return duration / MS_IN_HOUR;
        }
        return null;
    }

    public int getPrecision(int pointsRequired) {
        if (!isBounded()) {
            return 0;
        }
        if (pointsRequired == 0) {
            return 0;
        }
        long duration = getTo() - getFrom();

        long scale = duration / pointsRequired;

        return (int) Math.log10(scale);
    }

    /**
     * @return whole days in period
     */
    public Long getDaysInPeriod() {
        if (isBounded()) {
            long duration = getTo() - getFrom();
            long days = (duration / MS_IN_DAY);
            return days;

        }
        return null;
    }

    // Here for XML serialisation.
    public Long getFromMs() {
        return super.getFrom();
    }

    // Here for XML serialisation.
    public void setFromMs(Long from) {
        super.setFrom(from);
    }

    // Here for XML serialisation.
    public Long getToMs() {
        return super.getTo();
    }

    // Here for XML serialisation.
    public void setToMs(Long to) {
        super.setTo(to);
    }

    /**
     * Gets the duration as a user readable string.
     */
    public String getDuration() {
        if (!isBounded()) {
            return null;
        }
        long duration = getTo() - getFrom();
        int hours = (int) (duration / MS_IN_HOUR);
        duration = duration - (hours * MS_IN_HOUR);
        int minutes = (int) (duration / MS_IN_MINUTE);
        duration = duration - (minutes * MS_IN_MINUTE);
        int seconds = (int) (duration / MS_IN_SECOND);
        duration = duration - (seconds * MS_IN_SECOND);

        final StringBuilder sb = new StringBuilder();
        sb.append(zeroPad(2, Integer.toString(hours)));
        sb.append(":");
        sb.append(zeroPad(2, Integer.toString(minutes)));
        sb.append(":");
        sb.append(zeroPad(2, Integer.toString(seconds)));
        sb.append(".");
        sb.append(zeroPad(N3, Long.toString(duration)));

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
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("From: ");
        if (getFrom() == null) {
            builder.append("null");
        } else {
            builder.append(new Date(getFrom()));
        }
        builder.append(", To: ");
        if (getTo() == null) {
            builder.append("null");
        } else {
            builder.append(new Date(getTo()));
        }
        builder.append(", Duration: ");
        builder.append(getDuration());
        builder.append("]");
        return builder.toString();
    }

}
