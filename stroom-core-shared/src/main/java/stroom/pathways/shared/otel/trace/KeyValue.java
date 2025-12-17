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

package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class KeyValue {

    @JsonProperty("key")
    private final String key;

    @JsonProperty("value")
    private final AnyValue value;

    @JsonCreator
    public KeyValue(@JsonProperty("key") final String key,
                    @JsonProperty("value") final AnyValue value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public AnyValue getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyValue keyValue = (KeyValue) o;
        return Objects.equals(key, keyValue.key) &&
               Objects.equals(value, keyValue.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "KeyValue{" +
               "key='" + key + '\'' +
               ", value=" + value +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<KeyValue, Builder> {

        private String key;
        private AnyValue value;

        private Builder() {
        }

        private Builder(final KeyValue keyValue) {
            this.key = keyValue.key;
            this.value = keyValue.value;
        }

        public Builder key(final String key) {
            this.key = key;
            return self();
        }

        public Builder value(final AnyValue value) {
            this.value = value;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public KeyValue build() {
            return new KeyValue(
                    key,
                    value
            );
        }
    }
}
