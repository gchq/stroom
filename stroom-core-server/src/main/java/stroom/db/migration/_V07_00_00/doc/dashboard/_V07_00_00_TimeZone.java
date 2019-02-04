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

package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;
import stroom.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"use", "id", "offsetHours", "offsetMinutes"})
@XmlRootElement(name = "timeZone")
@XmlType(name = "TimeZone", propOrder = {"use", "id", "offsetHours", "offsetMinutes"})
public class _V07_00_00_TimeZone implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = 1200175661441813029L;

    @XmlElement(name = "use")
    private _V07_00_00_Use use;
    @XmlElement(name = "id")
    private String id;
    @XmlElement(name = "offsetHours")
    private Integer offsetHours;
    @XmlElement(name = "offsetMinutes")
    private Integer offsetMinutes;

    public _V07_00_00_TimeZone() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_TimeZone(final _V07_00_00_Use use, final String id, final Integer offsetHours, final Integer offsetMinutes) {
        this.use = use;
        this.id = id;
        this.offsetHours = offsetHours;
        this.offsetMinutes = offsetMinutes;
    }

    public static _V07_00_00_TimeZone local() {
        final _V07_00_00_TimeZone timeZone = new _V07_00_00_TimeZone();
        timeZone.use = _V07_00_00_Use.LOCAL;
        return timeZone;
    }

    public static _V07_00_00_TimeZone utc() {
        final _V07_00_00_TimeZone timeZone = new _V07_00_00_TimeZone();
        timeZone.use = _V07_00_00_Use.UTC;
        return timeZone;
    }

    public static _V07_00_00_TimeZone fromId(final String id) {
        final _V07_00_00_TimeZone timeZone = new _V07_00_00_TimeZone();
        timeZone.use = _V07_00_00_Use.ID;
        timeZone.id = id;
        return timeZone;
    }

    public static _V07_00_00_TimeZone fromOffset(final int offsetHours, final int offsetMinutes) {
        final _V07_00_00_TimeZone timeZone = new _V07_00_00_TimeZone();
        timeZone.use = _V07_00_00_Use.OFFSET;
        timeZone.offsetHours = offsetHours;
        timeZone.offsetMinutes = offsetMinutes;
        return timeZone;
    }

    public _V07_00_00_Use getUse() {
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

    public enum _V07_00_00_Use implements _V07_00_00_HasDisplayValue {
        LOCAL("Local"), UTC("UTC"), ID("Id"), OFFSET("Offset");

        private final String displayValue;

        _V07_00_00_Use(final String displayValue) {
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
