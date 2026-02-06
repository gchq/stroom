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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class Days {

    @JsonProperty
    private final List<Day> days;

    @JsonCreator
    public Days(@JsonProperty("days") final List<Day> days) {
        this.days = days;
    }

    public static Days create(final Set<Day> set) {
        if (NullSafe.isEmptyCollection(set)) {
            return new Days(null);
        }
        return new Days(set.stream().collect(Collectors.toList()));
    }

    public List<Day> getDays() {
        return days;
    }

    @JsonIgnore
    public boolean isIncluded(final Day day) {
        return days != null && days.contains(day);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Days days1 = (Days) o;
        return Objects.equals(days, days1.days);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(days);
    }

    @Override
    public String toString() {
        if (NullSafe.isEmptyCollection(days)) {
            return "None";
        }
        boolean all = true;
        boolean contiguous = true;
        Day start = null;
        Day end = null;
        Day last = null;
        final StringBuilder sb = new StringBuilder();
        for (final Day day : Day.ALL) {
            if (!days.contains(day)) {
                all = false;
            } else {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(day.getShortForm());

                if (start == null) {
                    start = day;
                } else if (!days.contains(last)) {
                    contiguous = false;
                }
                end = day;
            }
            last = day;
        }

        if (all) {
            return "All";
        }
        if (contiguous && start != null) {
            return start.getShortForm() + "-" + end.getShortForm();
        }
        return sb.toString();
    }
}
