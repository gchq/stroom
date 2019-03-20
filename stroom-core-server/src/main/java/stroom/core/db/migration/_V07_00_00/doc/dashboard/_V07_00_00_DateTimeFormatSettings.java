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

package stroom.core.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"pattern", "timeZone"})
@XmlRootElement(name = "dateTimeFormatSettings")
@XmlType(name = "DateTimeFormatSettings", propOrder = {"pattern", "timeZone"})
public class _V07_00_00_DateTimeFormatSettings implements _V07_00_00_FormatSettings {
    private static final long serialVersionUID = 9145624653060319801L;

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

    @XmlElement(name = "pattern")
    private String pattern;
    @XmlElement(name = "timeZone")
    private _V07_00_00_TimeZone timeZone;

    public String getPattern() {
        return pattern;
    }

    public void setPattern(final String pattern) {
        this.pattern = pattern;
    }

    public _V07_00_00_TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(final _V07_00_00_TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    @XmlTransient
    @Override
    public boolean isDefault() {
        return pattern == null || pattern.equals(DEFAULT_PATTERN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        _V07_00_00_DateTimeFormatSettings that = (_V07_00_00_DateTimeFormatSettings) o;

        return new _V07_00_00_EqualsBuilder()
                .append(pattern, that.pattern)
                .append(timeZone, that.timeZone)
                .isEquals();
    }

    @Override
    public int hashCode() {
        _V07_00_00_HashCodeBuilder hashCodeBuilder = new _V07_00_00_HashCodeBuilder();
        hashCodeBuilder.append(pattern);
        hashCodeBuilder.append(timeZone);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        return "DateTimeFormatSettings{" +
                "pattern='" + pattern + '\'' +
                ", timeZone=" + timeZone +
                '}';
    }
}
