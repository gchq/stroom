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

import stroom.query.api.UserTimeZone.Use;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DateTimeSettings {

    @Schema(description = "A date time formatting pattern string conforming to the specification of " +
            "java.time.format.DateTimeFormatter")
    @JsonProperty
    private final String dateTimePattern;

    @JsonProperty
    private final UserTimeZone timeZone;

    @Schema(description = "The local zone id to use when formatting date values in the search results. The " +
            "value is the string form of a java.time.ZoneId",
            required = true)
    @JsonProperty
    private final String localZoneId;

    @Schema(description = "The time in milliseconds since epoch to use as the reference time for relative date " +
            "functions like `day()`. Typically this is the current time when the query is executed. If null the " +
            "current time will be assumed.",
            required = true)
    // This is used in the API, so can't expect API users to set the reference time to now every time they
    // run an API search that uses related date terms.
    @JsonProperty
    private final Long referenceTime;

    /**
     * @param dateTimePattern The client date time pattern to use by default for formatting search results that contain
     *                        dates.
     * @param timeZone        The client timezone to use by default for formatting search results that contain dates.
     * @param localZoneId     The locale to use when formatting date values in the search results. The value is the
     *                        string form of a {@link java.time.ZoneId zoneId}
     */
    @JsonCreator
    public DateTimeSettings(@JsonProperty("dateTimePattern") final String dateTimePattern,
                            @JsonProperty("timeZone") final UserTimeZone timeZone,
                            @JsonProperty("localZoneId") final String localZoneId,
                            @JsonProperty("referenceTime") final Long referenceTime) {
        this.dateTimePattern = dateTimePattern;
        this.timeZone = timeZone;
        this.localZoneId = localZoneId;
        this.referenceTime = referenceTime;
    }

    public String getDateTimePattern() {
        return dateTimePattern;
    }

    public UserTimeZone getTimeZone() {
        return timeZone;
    }

    public String getLocalZoneId() {
        return localZoneId;
    }

    public Long getReferenceTime() {
        return referenceTime;
    }

    /**
     * @return A copy of this but without the referenceTime.
     */
    public DateTimeSettings withoutReferenceTime() {
        return new DateTimeSettings(dateTimePattern, timeZone, localZoneId, null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DateTimeSettings)) {
            return false;
        }
        final DateTimeSettings that = (DateTimeSettings) o;
        return Objects.equals(dateTimePattern, that.dateTimePattern) &&
                Objects.equals(timeZone, that.timeZone) &&
                Objects.equals(localZoneId, that.localZoneId) &&
                Objects.equals(referenceTime, that.referenceTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateTimePattern, timeZone, localZoneId, referenceTime);
    }

    @Override
    public String toString() {
        return "DateTimeSettings{" +
                "dateTimePattern='" + dateTimePattern + '\'' +
                ", timeZone=" + timeZone +
                ", localZoneId='" + localZoneId + '\'' +
                ", referenceTime=" + referenceTime +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String dateTimePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
        private UserTimeZone timeZone = UserTimeZone.builder().use(Use.UTC).build();
        private String localZoneId = "Z";
        private Long referenceTime;

        private Builder() {
        }

        private Builder(final DateTimeSettings dateTimeSettings) {
            this.dateTimePattern = dateTimeSettings.dateTimePattern;
            this.timeZone = dateTimeSettings.timeZone;
            this.localZoneId = dateTimeSettings.localZoneId;
        }

        public Builder dateTimePattern(final String dateTimePattern) {
            this.dateTimePattern = dateTimePattern;
            return this;
        }

        public Builder timeZone(final UserTimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder localZoneId(final String localZoneId) {
            this.localZoneId = localZoneId;
            return this;
        }

        public Builder referenceTime(final Long referenceTime) {
            this.referenceTime = referenceTime;
            return this;
        }

        public DateTimeSettings build() {
            if (referenceTime == null) {
                referenceTime = System.currentTimeMillis();
            }
            return new DateTimeSettings(dateTimePattern, timeZone, localZoneId, referenceTime);
        }
    }
}
