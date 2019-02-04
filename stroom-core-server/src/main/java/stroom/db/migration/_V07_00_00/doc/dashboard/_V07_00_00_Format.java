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
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "settings", "wrap"})
@XmlRootElement(name = "format")
@XmlType(name = "Format", propOrder = {"type", "settings", "wrap"})
public class _V07_00_00_Format implements Serializable {
    private static final long serialVersionUID = -5380825645719299089L;

    public static List<_V07_00_00_Type> TYPES = Arrays.asList(_V07_00_00_Type.GENERAL, _V07_00_00_Type.NUMBER, _V07_00_00_Type.DATE_TIME, _V07_00_00_Type.TEXT);

    @XmlElement(name = "type")
    private _V07_00_00_Type type;
    @XmlElements({@XmlElement(name = "numberFormatSettings", type = _V07_00_00_NumberFormatSettings.class),
            @XmlElement(name = "dateTimeFormatSettings", type = _V07_00_00_DateTimeFormatSettings.class)})
    private _V07_00_00_FormatSettings settings;
    @XmlElement(name = "wrap")
    private Boolean wrap;

    public _V07_00_00_Format() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_Format(final _V07_00_00_Type type) {
        this.type = type;
    }

    public _V07_00_00_Format(final _V07_00_00_Type type, final _V07_00_00_FormatSettings settings) {
        this.type = type;
        this.settings = settings;
    }

    public _V07_00_00_Format(_V07_00_00_Type type, _V07_00_00_FormatSettings settings, Boolean wrap) {
        this.type = type;
        this.settings = settings;
        this.wrap = wrap;
    }

    public _V07_00_00_Type getType() {
        return type;
    }

    public void setType(final _V07_00_00_Type type) {
        this.type = type;
    }

    public _V07_00_00_FormatSettings getSettings() {
        return settings;
    }

    public void setSettings(final _V07_00_00_FormatSettings settings) {
        this.settings = settings;
    }

    public Boolean getWrap() {
        return wrap;
    }

    public void setWrap(final Boolean wrap) {
        this.wrap = wrap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        _V07_00_00_Format format = (_V07_00_00_Format) o;

        return new _V07_00_00_EqualsBuilder()
                .append(type, format.type)
                .append(settings, format.settings)
                .append(wrap, format.wrap)
                .isEquals();
    }

    @Override
    public int hashCode() {
        _V07_00_00_HashCodeBuilder hashCodeBuilder = new _V07_00_00_HashCodeBuilder();
        hashCodeBuilder.append(type);
        hashCodeBuilder.append(settings);
        hashCodeBuilder.append(wrap);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        return "Format{" +
                "type=" + type +
                ", settings=" + settings +
                ", wrap=" + wrap +
                '}';
    }

    public enum _V07_00_00_Type implements _V07_00_00_HasDisplayValue {
        GENERAL("General"), NUMBER("Number"), DATE_TIME("Date Time"), TEXT("Text");

        private final String displayValue;

        _V07_00_00_Type(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
