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

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "settings", "wrap"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "Describes the formatting that will be applied to values in a field")
public class Format {

    public static final List<Type> TYPES = Arrays.asList(Type.GENERAL, Type.NUMBER, Type.DATE_TIME, Type.TEXT);
    public static final Format GENERAL = new Builder().type(Type.GENERAL).build();
    public static final Format NUMBER = new Builder().type(Type.NUMBER).build();
    public static final Format DATE_TIME = new Builder().type(Type.DATE_TIME).build();
    public static final Format TEXT = new Builder().type(Type.TEXT).build();

    @Schema(description = "The formatting type to apply",
            example = "NUMBER",
            required = true)
    @JsonProperty
    private final Type type;
    @JsonProperty
    private final FormatSettings settings;
    @JsonProperty
    private final Boolean wrap;

    @JsonCreator
    public Format(@JsonProperty("type") final Type type,
                  @JsonProperty("settings") final FormatSettings settings,
                  @JsonProperty("wrap") final Boolean wrap) {
        this.type = type;
        this.settings = settings;
        this.wrap = wrap;
    }

    public Type getType() {
        return type;
    }

    public FormatSettings getSettings() {
        return settings;
    }

    public Boolean getWrap() {
        return wrap;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Format format = (Format) o;
        return type == format.type &&
                Objects.equals(settings, format.settings) &&
                Objects.equals(wrap, format.wrap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, settings, wrap);
    }

    @Override
    public String toString() {
        return "Format{" +
                "type=" + type +
                ", settings=" + settings +
                ", wrap=" + wrap +
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
     * Builder for constructing a {@link Format}
     */
    public static final class Builder {

        private Type type;
        private FormatSettings settings;
        private Boolean wrap;

        private Builder() {
        }

        private Builder(final Format format) {
            this.type = format.type;
            this.settings = format.settings;
            this.wrap = format.wrap;
        }

        /**
         * @param value XXXXXXXXXXXXXXXX
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder type(final Type value) {
            this.type = value;
            return this;
        }

        public Builder settings(final FormatSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder wrap(final Boolean wrap) {
            this.wrap = wrap;
            return this;
        }

        public Format build() {
            return new Format(type, settings, wrap);
        }
    }


    // --------------------------------------------------------------------------------


    public enum Type implements HasDisplayValue {
        GENERAL("General"),
        NUMBER("Number"),
        DATE_TIME("Date Time"),
        TEXT("Text");

        private final String displayValue;

        Type(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
