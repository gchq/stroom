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

import stroom.docref.HasDisplayValue;

import java.util.List;
import java.util.Map;

public enum Day implements HasDisplayValue {
    MONDAY("Monday", "Mon", 1),
    TUESDAY("Tuesday", "Tue", 2),
    WEDNESDAY("Wednesday", "Wed", 3),
    THURSDAY("Thursday", "Thu", 4),
    FRIDAY("Friday", "Fri", 5),
    SATURDAY("Saturday", "Sat", 6),
    SUNDAY("Sunday", "Sun", 7);

    public static final List<Day> ALL = List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);
    public static final Map<Integer, Day> DAY_MAP = Map.of(
            MONDAY.value, MONDAY,
            TUESDAY.value, TUESDAY,
            WEDNESDAY.value, WEDNESDAY,
            THURSDAY.value, THURSDAY,
            FRIDAY.value, FRIDAY,
            SATURDAY.value, SATURDAY,
            SUNDAY.value, SUNDAY);

    public static Day getDayByValue(final int value) {
        return DAY_MAP.get(value);
    }

    private final String displayValue;
    private final String shortForm;
    private final int value;

    Day(final String displayValue,
        final String shortForm,
        final int value) {
        this.displayValue = displayValue;
        this.shortForm = shortForm;
        this.value = value;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getShortForm() {
        return shortForm;
    }

    /**
     * Gets the day-of-week {@code int} value.
     * The values are numbered following the ISO-8601 standard, from 1 (Monday) to 7 (Sunday).
     *
     * @return the day-of-week, from 1 (Monday) to 7 (Sunday)
     */
    public int getValue() {
        return value;
    }
}
