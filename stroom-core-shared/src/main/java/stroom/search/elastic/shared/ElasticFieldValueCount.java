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

package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A value of a field and the number of sampled documents holding it.
@JsonPropertyOrder({"value", "count"})
@JsonInclude(Include.NON_NULL)
public final class ElasticFieldValueCount {

    @JsonProperty
    private final String value;
    @JsonProperty
    private final Long count;

    /// @param value the value, as text
    /// @param count the number of sampled documents holding the value
    @JsonCreator
    public ElasticFieldValueCount(@JsonProperty("value") final String value,
                                  @JsonProperty("count") final Long count) {
        this.value = value;
        this.count = count;
    }

    /// @return the value, as text
    public String getValue() {
        return value;
    }

    /// @return the number of sampled documents holding the value
    public Long getCount() {
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
        final ElasticFieldValueCount that = (ElasticFieldValueCount) o;
        return Objects.equals(value, that.value) &&
               Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, count);
    }

    @Override
    public String toString() {
        return "ElasticFieldValueCount{" +
               "value=" + value +
               ", count=" + count +
               '}';
    }
}
