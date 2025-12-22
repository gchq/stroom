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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "stateValueType",
        "hashLength"
})
@JsonInclude(Include.NON_NULL)
public class StateValueSchema {

    private static final StateValueType DEFAULT_VALUE_TYPE = StateValueType.VARIABLE;
    private static final HashLength DEFAULT_HASH_LENGTH = HashLength.INTEGER;

    @JsonProperty
    private final StateValueType stateValueType;

    @JsonPropertyDescription("The hash length to use for foreign keys")
    @JsonProperty
    private final HashLength hashLength;

    @JsonCreator
    public StateValueSchema(@JsonProperty("stateValueType") final StateValueType stateValueType,
                            @JsonProperty("hashLength") final HashLength hashLength) {
        this.stateValueType = NullSafe.requireNonNullElse(stateValueType, DEFAULT_VALUE_TYPE);
        this.hashLength = NullSafe.requireNonNullElse(hashLength, DEFAULT_HASH_LENGTH);
    }

    public StateValueType getStateValueType() {
        return stateValueType;
    }

    public HashLength getHashLength() {
        return hashLength;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StateValueSchema that = (StateValueSchema) o;
        return stateValueType == that.stateValueType &&
               hashLength == that.hashLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateValueType, hashLength);
    }

    @Override
    public String toString() {
        return "StateValueSchema{" +
               "stateValueType=" + stateValueType +
               ", hashLength=" + hashLength +
               '}';
    }

    public static class Builder extends AbstractBuilder<StateValueSchema, Builder> {

        private StateValueType stateValueType;
        private HashLength hashLength;

        public Builder() {
        }

        public Builder(final StateValueSchema schema) {
            if (schema != null) {
                this.stateValueType = schema.stateValueType;
                this.hashLength = schema.hashLength;
            }
        }

        public Builder stateValueType(final StateValueType stateValueType) {
            this.stateValueType = stateValueType;
            return self();
        }

        public Builder hashLength(final HashLength hashLength) {
            this.hashLength = hashLength;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public StateValueSchema build() {
            return new StateValueSchema(stateValueType, hashLength);
        }
    }
}
