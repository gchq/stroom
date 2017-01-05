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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "numberFormatSettings", propOrder = { "decimalPlaces", "useSeparator" })
public class NumberFormatSettings implements FormatSettings {
    private static final long serialVersionUID = 9145624653060319801L;

    public static final int DEFAULT_DECIMAL_PLACES = 0;
    public static final boolean DEFAULT_USE_SEPARATOR = false;

    @XmlElement(name = "decimalPlaces")
    private Integer decimalPlaces;
    @XmlElement(name = "useSeparator")
    private Boolean useSeparator;

    public NumberFormatSettings() {
    }

    public NumberFormatSettings(Integer decimalPlaces, Boolean useSeparator) {
        this.decimalPlaces = decimalPlaces;
        this.useSeparator = useSeparator;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(final Integer decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
    }

    public Boolean getUseSeparator() {
        return useSeparator;
    }

    public void setUseSeparator(final Boolean useSeparator) {
        this.useSeparator = useSeparator;
    }

    @Override
    @XmlTransient
    @JsonIgnore
    public boolean isDefault() {
        return (decimalPlaces == null || decimalPlaces.equals(DEFAULT_DECIMAL_PLACES))
                && (useSeparator == null || useSeparator.equals(DEFAULT_USE_SEPARATOR));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        NumberFormatSettings that = (NumberFormatSettings) o;

        return new EqualsBuilder()
                .append(decimalPlaces, that.decimalPlaces)
                .append(useSeparator, that.useSeparator)
                .isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(decimalPlaces);
        hashCodeBuilder.append(useSeparator);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        return "NumberFormatSettings{" +
                "decimalPlaces=" + decimalPlaces +
                ", useSeparator=" + useSeparator +
                '}';
    }
}
