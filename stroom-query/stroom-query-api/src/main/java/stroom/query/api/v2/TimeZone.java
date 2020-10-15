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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.docref.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({"use", "id", "offsetHours", "offsetMinutes"})
@XmlType(name = "TimeZone", propOrder = {"use", "id", "offsetHours", "offsetMinutes"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "The timezone to apply to a date time value")
@JsonInclude(Include.NON_NULL)
public final class TimeZone implements Serializable {
    private static final long serialVersionUID = 1200175661441813029L;

    @XmlElement
    //TODO needs better description
    @ApiModelProperty(
            value = "The required type of time zone",
            required = true)
    @JsonProperty
    private Use use;

    @XmlElement
    @ApiModelProperty(
            value = "The id of the time zone, conforming to java.time.ZoneId",
            example = "GMT")
    @JsonProperty
    private String id;

    @XmlElement
    @ApiModelProperty(
            value = "The number of hours this timezone is offset from UTC",
            example = "-1")
    @JsonProperty
    private Integer offsetHours;

    @XmlElement
    @ApiModelProperty(
            value = "The number of minutes this timezone is offset from UTC",
            example = "-30")
    @JsonProperty
    private Integer offsetMinutes;

    public TimeZone() {
    }

    @JsonCreator
    public TimeZone(@JsonProperty("use") final Use use,
                    @JsonProperty("id") final String id,
                    @JsonProperty("offsetHours") final Integer offsetHours,
                    @JsonProperty("offsetMinutes") final Integer offsetMinutes) {
        this.use = use;
        this.id = id;
        this.offsetHours = offsetHours;
        this.offsetMinutes = offsetMinutes;
    }

    public static TimeZone local() {
        final TimeZone timeZone = new TimeZone();
        timeZone.use = Use.LOCAL;
        return timeZone;
    }

    public static TimeZone utc() {
        final TimeZone timeZone = new TimeZone();
        timeZone.use = Use.UTC;
        return timeZone;
    }

    public static TimeZone fromId(final String id) {
        final TimeZone timeZone = new TimeZone();
        timeZone.use = Use.ID;
        timeZone.id = id;
        return timeZone;
    }

    public static TimeZone fromOffset(final int offsetHours, final int offsetMinutes) {
        final TimeZone timeZone = new TimeZone();
        timeZone.use = Use.OFFSET;
        timeZone.offsetHours = offsetHours;
        timeZone.offsetMinutes = offsetMinutes;
        return timeZone;
    }

    public Use getUse() {
        return use;
    }

    public void setUse(final Use use) {
        this.use = use;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getOffsetHours() {
        return offsetHours;
    }

    public void setOffsetHours(final Integer offsetHours) {
        this.offsetHours = offsetHours;
    }

    public Integer getOffsetMinutes() {
        return offsetMinutes;
    }

    public void setOffsetMinutes(final Integer offsetMinutes) {
        this.offsetMinutes = offsetMinutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TimeZone timeZone = (TimeZone) o;
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


    /**
     * Builder for constructing a {@link TimeZone timeZone}
     */
    public static class Builder {
        private Use use;

        private String id;

        private Integer offsetHours;

        private Integer offsetMinutes;

        public Builder() {
        }

        public Builder(final TimeZone timeZone) {
            this.use = timeZone.use;
            this.id = timeZone.id;
            this.offsetHours = timeZone.offsetHours;
            this.offsetMinutes = timeZone.offsetMinutes;
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

        public TimeZone build() {
            return new TimeZone(use, id, offsetHours, offsetMinutes);
        }
    }
}