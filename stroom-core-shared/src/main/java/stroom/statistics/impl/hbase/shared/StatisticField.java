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

package stroom.statistics.impl.hbase.shared;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"fieldName"})
@JsonInclude(Include.NON_NULL)
public class StatisticField implements HasDisplayValue, Comparable<StatisticField> {

    @JsonProperty
    private String fieldName;

    public StatisticField() {
    }

    @JsonCreator
    public StatisticField(@JsonProperty("fieldName") final String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(final String fieldName) {
        this.fieldName = fieldName;
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fieldName == null)
                ? 0
                : fieldName.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StatisticField other = (StatisticField) obj;
        if (fieldName == null) {
            return other.fieldName == null;
        } else {
            return fieldName.equals(other.fieldName);
        }
    }

    @Override
    public String toString() {
        return "StatisticField [fieldName=" + fieldName + "]";
    }

    public StatisticField deepCopy() {
        return new StatisticField(fieldName);
    }

}
