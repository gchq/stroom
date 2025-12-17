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
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder({"use", "id", "offsetHours", "offsetMinutes"})
@Schema(description = "The timezone to apply to a date time value")
@JsonInclude(Include.NON_DEFAULT)
public final class UserTimeZone {

    @Schema(description = "How the time zone will be specified, e.g. " +
                          "from provided client 'Local' time, " +
                          "'UTC', " +
                          "a recognised timezone 'Id' " +
                          "or an 'Offset' from UTC in hours and minutes.",
            required = true)
    @JsonProperty
    private final Use use;

    @Schema(description = "The id of the time zone, conforming to java.time.ZoneId",
            example = "GMT")
    @JsonProperty
    private final String id;

    @Schema(description = "The number of hours this timezone is offset from UTC",
            example = "-1")
    @JsonProperty
    private final Integer offsetHours;

    @Schema(description = "The number of minutes this timezone is offset from UTC",
            example = "-30")
    @JsonProperty
    private final Integer offsetMinutes;

    // Establish defaults for serialiser.
    @SuppressWarnings("unused")
    private UserTimeZone() {
        use = Use.UTC;
        id = "";
        offsetHours = 0;
        offsetMinutes = 0;
    }

    @JsonCreator
    public UserTimeZone(@JsonProperty("use") final Use use,
                        @JsonProperty("id") final String id,
                        @JsonProperty("offsetHours") final Integer offsetHours,
                        @JsonProperty("offsetMinutes") final Integer offsetMinutes) {
        this.use = NullSafe.requireNonNullElse(use, Use.UTC);
        this.id = NullSafe.requireNonNullElse(id, "");
        this.offsetHours = NullSafe.requireNonNullElse(offsetHours, 0);
        this.offsetMinutes = NullSafe.requireNonNullElse(offsetMinutes, 0);
    }

    public static UserTimeZone local() {
        return new UserTimeZone(Use.LOCAL, null, null, null);
    }

    public static UserTimeZone utc() {
        return new UserTimeZone(Use.UTC, null, null, null);
    }

    public static UserTimeZone fromId(final String id) {
        return new UserTimeZone(Use.ID, id, null, null);
    }

    public static UserTimeZone fromOffset(final int offsetHours, final int offsetMinutes) {
        return new UserTimeZone(Use.OFFSET, null, offsetHours, offsetMinutes);
    }

    public Use getUse() {
        return use;
    }

    public String getId() {
        return id;
    }

    public Integer getOffsetHours() {
        return offsetHours;
    }

    public Integer getOffsetMinutes() {
        return offsetMinutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserTimeZone timeZone = (UserTimeZone) o;
        return use == timeZone.use &&
               Objects.equals(id, timeZone.id) &&
               Objects.equals(offsetHours, timeZone.offsetHours) &&
               Objects.equals(offsetMinutes, timeZone.offsetMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(use, id, offsetHours, offsetMinutes);
    }

    @Override
    public String toString() {
        return "TimeZone{" +
               "use=" + use +
               ", id='" + id + '\'' +
               ", offsetHours=" + offsetHours +
               ", offsetMinutes=" + offsetMinutes +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public enum Use implements HasDisplayValue {
        LOCAL("Local"),
        UTC("UTC"),
        ID("Id"),
        OFFSET("Offset");

        private final String displayValue;

        Use(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public String toString() {
            return getDisplayValue();
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link UserTimeZone timeZone}
     */
    public static final class Builder {

        private Use use;
        private String id;
        private Integer offsetHours;
        private Integer offsetMinutes;

        private Builder() {
        }

        private Builder(final UserTimeZone timeZone) {
            if (timeZone != null) {
                this.use = timeZone.use;
                this.id = timeZone.id;
                this.offsetHours = timeZone.offsetHours;
                this.offsetMinutes = timeZone.offsetMinutes;
            }
        }

        /**
         * @param value The required type of time zone
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder use(final Use value) {
            this.use = value;
            return this;
        }

        /**
         * @param value The id of the time zone, conforming to java.time.ZoneId
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder id(final String value) {
            this.id = value;
            return this;
        }

        /**
         * @param value The number of hours this timezone is offset from UTC
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder offsetHours(final Integer value) {
            this.offsetHours = value;
            return this;
        }

        /**
         * @param value The number of minutes this timezone is offset from UTC
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder offsetMinutes(final Integer value) {
            this.offsetMinutes = value;
            return this;
        }

        public UserTimeZone build() {
            return new UserTimeZone(use, id, offsetHours, offsetMinutes);
        }
    }
}
