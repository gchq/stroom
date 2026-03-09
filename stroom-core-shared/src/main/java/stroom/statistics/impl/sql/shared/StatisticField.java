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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"fieldName"})
@JsonInclude(Include.NON_NULL)
public class StatisticField implements HasDisplayValue, Comparable<StatisticField> {

    @JsonProperty
    private final String fieldName;

    @JsonCreator
    public StatisticField(@JsonProperty("fieldName") final String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public int compareTo(final StatisticField o) {
        return fieldName.compareToIgnoreCase(o.fieldName);
    }

    @Override
    @JsonIgnore
    public String getDisplayValue() {
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StatisticField that = (StatisticField) o;
        return Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fieldName);
    }

    @Override
    public String toString() {
        return fieldName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String fieldName;

        private Builder() {
        }

        private Builder(final StatisticField statisticField) {
            this.fieldName = statisticField.fieldName;
        }

        public Builder fieldName(final String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public StatisticField build() {
            return new StatisticField(fieldName);
        }
    }
}
