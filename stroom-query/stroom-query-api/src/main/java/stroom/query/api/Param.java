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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A key value pair that describes a property of a query")
public final class Param {

    @Schema(description = "The property key")
    @JsonProperty
    private final String key;

    @Schema(description = "The property value")
    @JsonProperty
    private final String value;

    @JsonCreator
    public Param(@JsonProperty("key") final String key,
                 @JsonProperty("value") final String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
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
        final Param param = (Param) o;
        return Objects.equals(key, param.key) &&
                Objects.equals(value, param.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "Param{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link Param}
     */
    public static final class Builder {

        private String key;
        private String value;

        private Builder() {
        }

        private Builder(final Param param) {
            key = param.key;
            value = param.value;
        }

        /**
         * @param value The property key
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder key(final String value) {
            this.key = value;
            return this;
        }

        /**
         * @param value The property value
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder value(final String value) {
            this.value = value;
            return this;
        }

        public Param build() {
            return new Param(key, value);
        }
    }
}
