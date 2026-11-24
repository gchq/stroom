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

package stroom.query.api;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/// How a column's values are shown as durations.
@JsonPropertyOrder({"style", "pattern"})
@Schema(description = "The string formatting to apply to a duration value")
@JsonInclude(Include.NON_NULL)
public final class DurationFormatSettings implements FormatSettings {

    @Schema(description = "How the duration is written. Defaults to EXACT")
    @JsonProperty
    private final Style style;

    @Schema(description = "The pattern to use if the style is PATTERN, such as d.hh:mm:ss, of the units d, h, m, s " +
                          "and f (fractions of a second)")
    @JsonProperty
    private final String pattern;

    /// @param style   How the duration is written, or null for the default.
    /// @param pattern The pattern to use if the style is [Style#PATTERN], as [DurationPattern] reads it.
    @JsonCreator
    public DurationFormatSettings(@JsonProperty("style") final Style style,
                                  @JsonProperty("pattern") final String pattern) {
        this.style = Objects.requireNonNullElse(style, Style.EXACT);
        this.pattern = pattern;
    }

    /// @return How the duration is written.
    public Style getStyle() {
        return style;
    }

    /// @return The pattern to use if the style is [Style#PATTERN], as [DurationPattern] reads it.
    public String getPattern() {
        return pattern;
    }

    @Override
    @JsonIgnore
    public boolean isDefault() {
        return style == Style.EXACT;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DurationFormatSettings that = (DurationFormatSettings) o;
        return style == that.style && Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(style, pattern);
    }

    @Override
    public String toString() {
        return "DurationFormatSettings{" +
               "style=" + style +
               ", pattern='" + pattern + '\'' +
               '}';
    }


    // --------------------------------------------------------------------------------


    /// The ways that a duration can be written.
    public enum Style implements HasDisplayValue {
        /// Every unit that it needs, such as `2d1h30m`.
        EXACT("Exact"),
        /// ISO 8601, such as `PT49H30M`.
        ISO("ISO 8601"),
        /// With a pattern.
        PATTERN("Pattern");

        private final String displayValue;

        Style(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
