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

package stroom.util.shared.time;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"hour", "minute", "second"})
public class Time {

    public static final Time ZERO = new Time(0, 0, 0);

    /**
     * The hour.
     */
    @JsonProperty
    private final int hour;
    /**
     * The minute.
     */
    @JsonProperty
    private final int minute;
    /**
     * The second.
     */
    @JsonProperty
    private final int second;

    @JsonCreator
    public Time(@JsonProperty("hour") final int hour,
                @JsonProperty("minute") final int minute,
                @JsonProperty("second") final int second) {
        this.hour = Math.max(Math.min(hour, 23), 0);
        this.minute = Math.max(Math.min(minute, 59), 0);
        this.second = Math.max(Math.min(second, 59), 0);
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Time time = (Time) o;
        return hour == time.hour && minute == time.minute && second == time.second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hour, minute, second);
    }

    @Override
    public String toString() {
        return pad(hour) + ":" + pad(minute) + ":" + pad(second);
    }

    private String pad(final int num) {
        final String str = Integer.toString(num);
        if (str.length() == 1) {
            return "0" + str;
        }
        return str;
    }

    public static Time parse(final String time) {
        if (NullSafe.isBlankString(time)) {
            return ZERO;
        }
        final String[] parts = time.split(":");
        int hour = 0;
        int minute = 0;
        int second = 0;
        if (parts.length > 0) {
            hour = parsePart(parts[0]);
        }
        if (parts.length > 1) {
            minute = parsePart(parts[1]);
        }
        if (parts.length > 2) {
            second = parsePart(parts[2]);
        }
        return new Time(hour, minute, second);
    }

    private static int parsePart(String part) {
        try {
            if (!NullSafe.isBlankString(part)) {
                if (part.startsWith("0")) {
                    part = part.substring(1);
                }
                return Integer.parseInt(part);
            }
        } catch (final Exception e) {
            // Ignore
        }
        return 0;
    }

    public static boolean isValid(final String time) {
        if (NullSafe.isBlankString(time)) {
            return false;
        }
        final String[] parts = time.split(":");
        if (parts.length < 3) {
            return false;
        }

        return isValidPart(parts[0], 23) &&
               isValidPart(parts[1], 59) &&
               isValidPart(parts[2], 59);
    }

    private static boolean isValidPart(String part, final int max) {
        try {
            if (!NullSafe.isBlankString(part)) {
                if (part.startsWith("0")) {
                    part = part.substring(1);
                }
                final int val = Integer.parseInt(part);
                return val >= 0 && val <= max;
            }
        } catch (final Exception e) {
            // Ignore
        }
        return false;
    }
}
