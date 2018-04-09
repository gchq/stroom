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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.HashCodeBuilder;

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
public class Format implements Serializable {
    private static final long serialVersionUID = -5380825645719299089L;

    public static List<Type> TYPES = Arrays.asList(Type.GENERAL, Type.NUMBER, Type.DATE_TIME, Type.TEXT);

    @XmlElement(name = "type")
    private Type type;
    @XmlElements({@XmlElement(name = "numberFormatSettings", type = NumberFormatSettings.class),
            @XmlElement(name = "dateTimeFormatSettings", type = DateTimeFormatSettings.class)})
    private FormatSettings settings;
    @XmlElement(name = "wrap")
    private Boolean wrap;

    public Format() {
        // Default constructor necessary for GWT serialisation.
    }

    public Format(final Type type) {
        this.type = type;
    }

    public Format(final Type type, final FormatSettings settings) {
        this.type = type;
        this.settings = settings;
    }

    public Format(Type type, FormatSettings settings, Boolean wrap) {
        this.type = type;
        this.settings = settings;
        this.wrap = wrap;
    }

    public Type getType() {
        return type;
    }

    public void setType(final Type type) {
        this.type = type;
    }

    public FormatSettings getSettings() {
        return settings;
    }

    public void setSettings(final FormatSettings settings) {
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

        Format format = (Format) o;

        return new EqualsBuilder()
                .append(type, format.type)
                .append(settings, format.settings)
                .append(wrap, format.wrap)
                .isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
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

    public enum Type implements HasDisplayValue {
        GENERAL("General"), NUMBER("Number"), DATE_TIME("Date Time"), TEXT("Text");

        private final String displayValue;

        Type(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
