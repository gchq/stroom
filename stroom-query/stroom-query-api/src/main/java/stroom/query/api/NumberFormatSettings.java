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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder({"decimalPlaces", "useSeparator"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "The definition of a format to apply to numeric data")
public final class NumberFormatSettings implements FormatSettings {

    private static final int DEFAULT_DECIMAL_PLACES = 0;
    private static final boolean DEFAULT_USE_SEPARATOR = false;

    @Schema(description = "The number of decimal places",
            example = "2",
            required = true)
    @JsonProperty
    private final Integer decimalPlaces;

    @Schema(description = "Whether to use a thousands separator or not. Defaults to false",
            example = "true")
    @JsonProperty
    private final Boolean useSeparator;

    @JsonCreator
    public NumberFormatSettings(@JsonProperty("decimalPlaces") final Integer decimalPlaces,
                                @JsonProperty("useSeparator") final Boolean useSeparator) {
        this.decimalPlaces = decimalPlaces;
        this.useSeparator = useSeparator;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public Boolean getUseSeparator() {
        return useSeparator;
    }

    public boolean useSeparator() {
        return useSeparator != null && useSeparator;
    }

    @Override
    @JsonIgnore
    public boolean isDefault() {
        return (decimalPlaces == null || decimalPlaces.equals(DEFAULT_DECIMAL_PLACES))
                && (useSeparator == null || useSeparator.equals(DEFAULT_USE_SEPARATOR));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NumberFormatSettings that = (NumberFormatSettings) o;
        return Objects.equals(decimalPlaces, that.decimalPlaces) &&
                Objects.equals(useSeparator, that.useSeparator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decimalPlaces, useSeparator);
    }

    @Override
    public String toString() {
        return "NumberFormatSettings{" +
                "decimalPlaces=" + decimalPlaces +
                ", useSeparator=" + useSeparator +
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
     * Builder for constructing a {@link NumberFormatSettings}
     */
    public static final class Builder {

        private Integer decimalPlaces;
        private Boolean useSeparator;

        private Builder() {
        }

        private Builder(final NumberFormatSettings numberFormat) {
            this.decimalPlaces = numberFormat.decimalPlaces;
            this.useSeparator = numberFormat.useSeparator;
        }

        /**
         * @param value Number of decimal places to apply to the number format
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder decimalPlaces(final Integer value) {
            this.decimalPlaces = value;
            return this;
        }

        /**
         * @param value Whether to use a thousands separator or not.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder useSeparator(final Boolean value) {
            this.useSeparator = value;
            return this;
        }

        public NumberFormatSettings build() {
            return new NumberFormatSettings(
                    NullSafe.requireNonNullElse(decimalPlaces, DEFAULT_DECIMAL_PLACES),
                    NullSafe.requireNonNullElse(useSeparator, DEFAULT_USE_SEPARATOR));
        }
    }
}
