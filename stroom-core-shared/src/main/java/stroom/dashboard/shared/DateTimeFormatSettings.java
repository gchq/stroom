/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"pattern", "timeZone"})
@JsonInclude(Include.NON_NULL)
public class DateTimeFormatSettings implements FormatSettings {
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

    @JsonProperty
    private final String pattern;
    @JsonProperty
    private final TimeZone timeZone;

    @JsonCreator
    public DateTimeFormatSettings(@JsonProperty("pattern") final String pattern,
                                  @JsonProperty("timeZone") final TimeZone timeZone) {
        this.pattern = pattern;
        this.timeZone = timeZone;
    }

    public String getPattern() {
        return pattern;
    }

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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

    @Override
    public FormatSettings copy() {
        return new DateTimeFormatSettings(pattern, timeZone);
    }
}
