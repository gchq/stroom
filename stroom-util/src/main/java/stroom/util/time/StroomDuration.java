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

import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Objects;

/**
 * Class to represent a duration. Internally it stores the duration as a {@link Duration} object.
 * It can be parsed from an ISO-8601 string (e.g. P30D or P1DT6H or PT60S), a stroom {@link ModelStringUtil}
 * type duration string (e.g. 30d), or a raw milliseconds value.
 * <p>
 * Typically suited for durations up to numbers of days.  A day is considered as 24hrs. Does NOT
 * support ISO 8601 units of Months and Years.
 * <p>
 * If {@link StroomDuration} is created from a string then the original string representation
 * is held and used for later serialisation. This avoids java's desire to serialise P30D as P720H.
 * <p>
 * Delegates most methods of {@link Duration} so can be used anywhere a Duration can.
 */
public class StroomDuration implements Comparable<StroomDuration>, TemporalAmount {

    // Allows us to hold the original serialised form of the duration as a duration can have more
    // than one serialised form. Can be null.
    private final String valueAsStr;

    private final Duration duration;

    public static final StroomDuration ZERO = new StroomDuration(Duration.ZERO);

    private StroomDuration(final String valueAsStr,
                           final Duration duration) {
        Objects.requireNonNull(duration);
        this.duration = duration;

        if (valueAsStr != null && !valueAsStr.equals(duration.toString())) {
            // Custom string form so store it
            this.valueAsStr = valueAsStr;
        } else {
            this.valueAsStr = null;
        }
    }

    private StroomDuration(final TemporalAmount temporalAmount) {
        Objects.requireNonNull(temporalAmount);
        if (Duration.class.isAssignableFrom(temporalAmount.getClass())) {
            this.duration = (Duration) temporalAmount;
        } else {
            this.duration = Duration.from(temporalAmount);
        }
        final String durationStr = duration.toString();
        // Duration won't output in days, instead using multiple hours, e.g. P30D => PT720H
        // which is a bit grim, so do a simple hack to deal with whole numbers of days.
        if (durationStr.matches("^PT[0-9]+[hH]$")) {
            // get the number of hours
            final long hours = Long.parseLong(durationStr.substring(2, durationStr.length() - 1));
            final long remainderHours = hours % 24;
//            if (hours >= 24 && hours % 24 == 0) {
            if (hours >= 24) {
                String valueAsStr = "P" + hours / 24 + "D";
                if (remainderHours > 0) {
                    valueAsStr += "T" + remainderHours + "H";
                }
                // Just make sure our constructed string is a valid ISO 8601 string
                Duration.parse(valueAsStr);
                this.valueAsStr = valueAsStr;
            } else {
                this.valueAsStr = null;
            }
        } else {
            this.valueAsStr = null;
        }
    }

    @JsonCreator
    public static StroomDuration parse(final String value) {
        return new StroomDuration(value, parseToDuration(value));
    }

    @JsonCreator
    public static StroomDuration parse(final long value) {
        return StroomDuration.ofMillis(value);
    }

    public static StroomDuration of(final TemporalAmount temporalAmount) {
        return new StroomDuration(temporalAmount);
    }

    public static StroomDuration of(final long amount, final TemporalUnit unit) {
        return new StroomDuration(Duration.of(amount, unit));
    }

    public static StroomDuration ofDays(final long days) {
        return new StroomDuration(Duration.ofDays(days));
    }

    public static StroomDuration ofHours(final long hours) {
        return new StroomDuration(Duration.ofHours(hours));
    }

    public static StroomDuration ofMinutes(final long minutes) {
        return new StroomDuration(Duration.ofMinutes(minutes));
    }

    public static StroomDuration ofSeconds(final long seconds) {
        return new StroomDuration(Duration.ofSeconds(seconds));
    }

    public static StroomDuration ofMillis(final long millis) {
        return new StroomDuration(Duration.ofMillis(millis));
    }

    public static StroomDuration ofNanos(final long nanos) {
        return new StroomDuration(Duration.ofNanos(nanos));
    }

    @SuppressWarnings("unused")
    @JsonValue
    public String getValueAsStr() {
        return valueAsStr != null
                ? valueAsStr
                : duration.toString();
    }

    @JsonIgnore
    public Duration getDuration() {
        return duration;
    }

    private static Duration parseToDuration(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        } else {
            if (value.startsWith("P")) {
                // This is ISO 8601 format so use Duration to parse it
                return Duration.parse(value);
            } else {
                // Not ISO 8601 so have a go with our ModelStringUtil format
                return Duration.ofMillis(ModelStringUtil.parseDurationString(value));
            }
        }
    }

    public long toMillis() {
        return duration.toMillis();
    }

    public long toNanos() {
        return duration.toNanos();
    }

    @JsonIgnore
    public boolean isZero() {
        return duration.isZero();
    }

    @Override
    public String toString() {
        return getValueAsStr();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StroomDuration that = (StroomDuration) o;
        return duration.equals(that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration);
    }

    @Override
    public int compareTo(final StroomDuration o) {
        return duration.compareTo(o.duration);
    }

    @Override
    public long get(final TemporalUnit unit) {
        return duration.get(unit);
    }

    @JsonIgnore
    @Override
    public List<TemporalUnit> getUnits() {
        return duration.getUnits();
    }

    @Override
    public Temporal addTo(final Temporal temporal) {
        return duration.addTo(temporal);
    }

    @Override
    public Temporal subtractFrom(final Temporal temporal) {
        return duration.subtractFrom(temporal);
    }
}
