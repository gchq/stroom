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

package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "valueType"
})
@JsonInclude(Include.NON_NULL)
public class HistogramValueSchema {

    private static final MaxValueSize DEFAULT_VALUE_TYPE = MaxValueSize.TWO;

    @JsonProperty
    private final MaxValueSize valueType;

    @JsonCreator
    public HistogramValueSchema(@JsonProperty("valueType") final MaxValueSize valueType) {
        this.valueType = NullSafe.requireNonNullElse(valueType, DEFAULT_VALUE_TYPE);
    }

    public MaxValueSize getValueType() {
        return valueType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HistogramValueSchema that = (HistogramValueSchema) o;
        return valueType == that.valueType;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(valueType);
    }

    @Override
    public String toString() {
        return "HistogramValueSchema{" +
               "valueType=" + valueType +
               '}';
    }

    public static class Builder extends AbstractBuilder<HistogramValueSchema, Builder> {

        private MaxValueSize valueType;

        public Builder() {
        }

        public Builder(final HistogramValueSchema schema) {
            if (schema != null) {
                this.valueType = schema.valueType;
            }
        }

        public Builder valueType(final MaxValueSize valueType) {
            this.valueType = valueType;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public HistogramValueSchema build() {
            return new HistogramValueSchema(valueType);
        }
    }
}
