/*
 * Copyright 2023 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ColumnValue {

    @JsonProperty
    private final String value;
    @JsonProperty
    private final String matchingRule;
    @JsonProperty
    private final boolean available;

    @JsonCreator
    public ColumnValue(@JsonProperty("value") final String value,
                       @JsonProperty("matchingRule") final String matchingRule,
                       @JsonProperty("available") final Boolean available) {
        this.value = value;
        this.matchingRule = matchingRule;
        this.available = NullSafe.isTrue(available);
    }

    public String getValue() {
        return value;
    }

    public String getMatchingRule() {
        return matchingRule;
    }

    /// @return True if at least one row with this value passes all of the table's filters, ignoring this
    /// column's own ticks, i.e. the value can currently appear in the table.
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnValue that = (ColumnValue) o;
        return available == that.available &&
               Objects.equals(value, that.value) &&
               Objects.equals(matchingRule, that.matchingRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, matchingRule, available);
    }

    @Override
    public String toString() {
        return "ColumnValue{" +
               "value='" + value + '\'' +
               ", matchingRule='" + matchingRule + '\'' +
               ", available=" + available +
               '}';
    }
}
