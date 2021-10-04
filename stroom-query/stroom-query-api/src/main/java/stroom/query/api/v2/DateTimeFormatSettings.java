/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Class for describing the format to use for formatting a date time value
 */
@JsonPropertyOrder({"pattern", "timeZone"})
@Schema(description = "The string formatting to apply to a date value")
@JsonInclude(Include.NON_NULL)
public final class DateTimeFormatSettings implements FormatSettings {

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

    @Schema(description = "A date time formatting pattern string conforming to the specification of " +
            "java.time.format.DateTimeFormatter",
            required = true)
    @JsonProperty
    private final String pattern;

    @Schema(required = true)
    @JsonProperty
    private final TimeZone timeZone;

    /**
     * @param pattern  A date time formatting pattern string conforming to the specification of
     *                 {@link java.time.format.DateTimeFormatter}
     * @param timeZone The time zone to use when formatting the date time value
     */
    @JsonCreator
    public DateTimeFormatSettings(@JsonProperty("pattern") final String pattern,
                                  @JsonProperty("timeZone") final TimeZone timeZone) {
        this.pattern = pattern;
        this.timeZone = timeZone;
    }

    /**
     * @return The format pattern string, conforming to {@link java.time.format.DateTimeFormatter}
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * @return The the {@link TimeZone timeZone} to use when formatting the date
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    @JsonIgnore
    public boolean isDefault() {
        return pattern == null || pattern.equals(DEFAULT_PATTERN);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DateTimeFormatSettings that = (DateTimeFormatSettings) o;
        return Objects.equals(pattern, that.pattern) &&
                Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, timeZone);
    }

    @Override
    public String toString() {
        return "DateTimeFormatSettings{" +
                "pattern='" + pattern + '\'' +
                ", timeZone=" + timeZone +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link DateTimeFormatSettings dateTimeFormat}
     */
    public static final class Builder {

        private String pattern;
        private TimeZone timeZone;

        private Builder() {
        }

        private Builder(final DateTimeFormatSettings dateTimeFormat) {
            this.pattern = dateTimeFormat.pattern;
            if (dateTimeFormat.timeZone != null) {
                this.timeZone = dateTimeFormat.timeZone;
            }
        }

        /**
         * @param value The format pattern string, conforming to {@link java.time.format.DateTimeFormatter}
         * @return this builder, enabling method chaining
         */
        public Builder pattern(final String value) {
            this.pattern = value;
            return this;
        }

        /**
         * @param value Set the {@link TimeZone timeZone} to use when formatting the date
         * @return this builder, enabling method chaining
         */
        public Builder timeZone(final TimeZone value) {
            this.timeZone = value;
            return this;
        }

        public DateTimeFormatSettings build() {
            return new DateTimeFormatSettings(pattern, timeZone);
        }
    }
}
