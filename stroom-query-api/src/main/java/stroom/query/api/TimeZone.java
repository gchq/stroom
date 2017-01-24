/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"use", "id", "offsetHours", "offsetMinutes"})
@XmlType(name = "timeZone", propOrder = {"use", "id", "offsetHours", "offsetMinutes"})
public class TimeZone implements Serializable {
    private static final long serialVersionUID = 1200175661441813029L;

    private Use use;
    private String id;
    private Integer offsetHours;
    private Integer offsetMinutes;

    public TimeZone() {
    }

    public TimeZone(final Use use, final String id, final Integer offsetHours, final Integer offsetMinutes) {
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

    @XmlElement
    public Use getUse() {
        return use;
    }

    public void setUse(final Use use) {
        this.use = use;
    }

    @XmlElement
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    @XmlElement
    public Integer getOffsetHours() {
        return offsetHours;
    }

    public void setOffsetHours(final Integer offsetHours) {
        this.offsetHours = offsetHours;
    }

    @XmlElement
    public Integer getOffsetMinutes() {
        return offsetMinutes;
    }

    public void setOffsetMinutes(final Integer offsetMinutes) {
        this.offsetMinutes = offsetMinutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeZone)) return false;

        final TimeZone timeZone = (TimeZone) o;

        if (use != timeZone.use) return false;
        if (id != null ? !id.equals(timeZone.id) : timeZone.id != null) return false;
        if (offsetHours != null ? !offsetHours.equals(timeZone.offsetHours) : timeZone.offsetHours != null)
            return false;
        return offsetMinutes != null ? offsetMinutes.equals(timeZone.offsetMinutes) : timeZone.offsetMinutes == null;
    }

    @Override
    public int hashCode() {
        int result = use != null ? use.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (offsetHours != null ? offsetHours.hashCode() : 0);
        result = 31 * result + (offsetMinutes != null ? offsetMinutes.hashCode() : 0);
        return result;
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
        LOCAL("Local"), UTC("UTC"), ID("Id"), OFFSET("Offset");

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
}
