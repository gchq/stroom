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

package stroom.statistics.impl.sql.shared;

import stroom.docref.HasDisplayValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum used to hold the intervals used in storing the event stats. The time of
 * the event is rounded to two levels, the row key level and the column level
 * within each row. The column interval is the finest granularity available. If
 * the column interval is too small relative to the row key interval then you
 * will end up with too many columns.
 */
public enum EventStoreTimeIntervalEnum implements HasDisplayValue {
    // IMPORTANT - Each interval must be exactly divisible into the next
    // largest. This is to allow us to roll up the
    // times from one store to the next without knowing the original exact time

    // Hourly row key with 3600 1sec column intervals
    SECOND(3_600_000L, 1_000L, "Second", "s"),
    // Daily row key with 1440 1min column intervals
    MINUTE(86_400_000L, 60_000L, "Minute", "m"),
    // Four Weekly row key with 672 1hour column intervals
    HOUR(2_419_200_000L, 3_600_000L, "Hour", "h"),
    // Fifty two Weekly with 364 1day column intervals
    DAY(31_449_600_000L, 86_400_000L, "Day", "d");

    private static final Map<Long, EventStoreTimeIntervalEnum> fromColumnIntervalMap = new HashMap<>();
    private static final Map<String, EventStoreTimeIntervalEnum> fromShortnameMap = new HashMap<>();

    static {
        for (final EventStoreTimeIntervalEnum interval : values()) {
            fromColumnIntervalMap.put(interval.columnInterval, interval);
            fromShortnameMap.put(interval.shortName, interval);
        }
    }

    private final long rowKeyInterval;
    private final long columnInterval;
    private final String longName;
    private final String shortName;

    /**
     * Constructor
     *
     * @param rowKeyInterval The time interval in milliseconds for the row key
     * @param columnInterval The time interval in milliseconds for the column qualifier
     * @param longName       The Human readable name for the interval
     * @param shortName      The short name for the interval, used in the hbase table name
     */
    EventStoreTimeIntervalEnum(final long rowKeyInterval, final long columnInterval, final String longName,
                               final String shortName) {
        this.rowKeyInterval = rowKeyInterval;
        this.columnInterval = columnInterval;
        this.longName = longName;
        this.shortName = shortName;
    }

    public static EventStoreTimeIntervalEnum fromColumnInterval(final long columnInterval) {
        try {
            return fromColumnIntervalMap.get(columnInterval);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException(
                    "The provided columnInterval [" + columnInterval + "] is not a valid event store interval", e);
        }
    }

    public static EventStoreTimeIntervalEnum fromShortName(final String shortName) {
        try {
            return fromShortnameMap.get(shortName);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException(
                    "The provided shortName [" + shortName + "] is not a valid name for an event store", e);
        }
    }

    /**
     * @return the time interval used for the row key in milliseconds
     */
    public long rowKeyInterval() {
        return this.rowKeyInterval;
    }

    /**
     * @return the time interval used for the column qualifier in milliseconds
     */
    public long columnInterval() {
        return this.columnInterval;
    }

    /**
     * @return the readable name of the row key interval, e.g 'Second'
     */
    public String longName() {
        return this.longName;
    }

    /**
     * @return the short code name of the row key interval, e.g 's'
     */
    public String shortName() {
        return this.shortName;
    }

    @Override
    public String getDisplayValue() {
        return this.longName;
    }

    public String getRowKeyIntervalAsString() {
        return timeIntervalToString(rowKeyInterval);

    }

    private String timeIntervalToString(final long timeMs) {
        final double seconds = timeMs / 1000;
        final double mintes = timeMs / (1000 * 60);
        final double hours = timeMs / (1000 * 60 * 60);
        final double days = timeMs / (1000 * 60 * 60 * 24);
        final double weeks = timeMs / (1000 * 60 * 60 * 24 * 7);

        final StringBuilder sb = new StringBuilder();
        sb.append("seconds: ");
        sb.append(seconds);
        sb.append(",  mintes: ");
        sb.append(mintes);
        sb.append(",  hours: ");
        sb.append(hours);
        sb.append(",  days: ");
        sb.append(days);
        sb.append(",  weeks: ");
        sb.append(weeks);

        return sb.toString();
    }

    public long roundTimeToColumnInterval(final long timestampMillis) {
        return roundTimeToInterval(timestampMillis, this.columnInterval);
    }

    public long roundTimeToRowKeyInterval(final long timestampMillis) {
        return roundTimeToInterval(timestampMillis, this.rowKeyInterval);
    }

    private long roundTimeToInterval(final long timestampMillis, final long intervalMillis) {
        return (timestampMillis / intervalMillis) * intervalMillis;
    }

    public static class InvalidTimeIntervalException extends RuntimeException {
        private static final long serialVersionUID = -7383690663978635032L;

        public InvalidTimeIntervalException(final String msg) {
            super(msg);
        }
    }
}
