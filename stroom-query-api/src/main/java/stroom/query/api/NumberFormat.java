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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"decimalPlaces", "useSeparator"})
@XmlType(name = "NumberFormat", propOrder = {"decimalPlaces", "useSeparator"})
@XmlAccessorType(XmlAccessType.FIELD)
public class NumberFormat implements Serializable {
    private static final long serialVersionUID = 9145624653060319801L;

    @XmlElement
    private Integer decimalPlaces;
    @XmlElement
    private Boolean useSeparator;

    public NumberFormat() {
    }

    public NumberFormat(Integer decimalPlaces, Boolean useSeparator) {
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

    public boolean useSeparator() {
        return useSeparator != null && useSeparator;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof NumberFormat)) return false;

        final NumberFormat that = (NumberFormat) o;

        if (decimalPlaces != null ? !decimalPlaces.equals(that.decimalPlaces) : that.decimalPlaces != null)
            return false;
        return useSeparator != null ? useSeparator.equals(that.useSeparator) : that.useSeparator == null;
    }

    @Override
    public int hashCode() {
        int result = decimalPlaces != null ? decimalPlaces.hashCode() : 0;
        result = 31 * result + (useSeparator != null ? useSeparator.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NumberFormat{" +
                "decimalPlaces=" + decimalPlaces +
                ", useSeparator=" + useSeparator +
                '}';
    }
}
