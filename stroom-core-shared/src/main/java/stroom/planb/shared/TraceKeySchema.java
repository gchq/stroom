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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "keyType",
        "hashLength",
        "temporalPrecision"
})
@JsonInclude(Include.NON_NULL)
public class TraceKeySchema extends StateKeySchema {

    public static final TemporalPrecision DEFAULT_TEMPORAL_PRECISION = TemporalPrecision.MILLISECOND;

    @JsonProperty
    private final TemporalPrecision temporalPrecision;

    @JsonCreator
    public TraceKeySchema(@JsonProperty("keyType") final KeyType keyType,
                          @JsonProperty("hashLength") final HashLength hashLength,
                          @JsonProperty("temporalPrecision") final TemporalPrecision temporalPrecision) {
        super(keyType, hashLength);
        this.temporalPrecision = temporalPrecision;
    }

    public TemporalPrecision getTemporalPrecision() {
        return temporalPrecision;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final TraceKeySchema that = (TraceKeySchema) o;
        return temporalPrecision == that.temporalPrecision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), temporalPrecision);
    }

    @Override
    public String toString() {
        return "TemporalStateKeySchema{" +
               "temporalPrecision=" + temporalPrecision +
               '}';
    }

    public static class Builder extends AbstractBuilder<TraceKeySchema, Builder> {

        private KeyType keyType = KeyType.VARIABLE;
        private HashLength hashLength = HashLength.INTEGER;
        private TemporalPrecision temporalPrecision;

        public Builder() {
        }

        public Builder(final TraceKeySchema schema) {
            this.keyType = schema.keyType;
            this.hashLength = schema.hashLength;
            this.temporalPrecision = schema.temporalPrecision;
        }

        public Builder keyType(final KeyType keyType) {
            this.keyType = keyType;
            return self();
        }

        public Builder hashLength(final HashLength hashLength) {
            this.hashLength = hashLength;
            return self();
        }

        public Builder temporalPrecision(final TemporalPrecision temporalPrecision) {
            this.temporalPrecision = temporalPrecision;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TraceKeySchema build() {
            return new TraceKeySchema(
                    keyType,
                    hashLength,
                    temporalPrecision);
        }
    }
}
