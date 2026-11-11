/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A value of a field and the number of documents that have it.
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FieldValueCount {

    @JsonProperty
    private final String value;
    @JsonProperty
    private final String termValue;
    @JsonProperty
    private final long count;

    /// Creates a value whose term value is the value as it is.
    ///
    /// @param value The field value.
    /// @param count The number of documents with the value.
    public FieldValueCount(final String value,
                           final Long count) {
        this(value, null, count);
    }

    /// @param value     The field value, as shown in the picker.
    /// @param termValue The value as it should appear in a term's value box so the term matches it, e.g.
    ///                  quoted or escaped as the data source's query syntax needs, or null to use the value
    ///                  as it is.
    /// @param count     The number of documents with the value.
    @JsonCreator
    public FieldValueCount(@JsonProperty("value") final String value,
                           @JsonProperty("termValue") final String termValue,
                           @JsonProperty("count") final Long count) {
        this.value = value;
        this.termValue = termValue;
        this.count = count == null
                ? 0
                : count;
    }

    /// @return The field value.
    public String getValue() {
        return value;
    }

    /// @return The value as it should appear in a term's value box, or null to use the value as it is.
    public String getTermValue() {
        return termValue;
    }

    /// @return The number of documents with the value.
    public long getCount() {
        return count;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FieldValueCount that = (FieldValueCount) o;
        return count == that.count
               && Objects.equals(value, that.value)
               && Objects.equals(termValue, that.termValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, termValue, count);
    }

    @Override
    public String toString() {
        return value + "=" + count;
    }
}
