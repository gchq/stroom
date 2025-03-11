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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "statisticField", propOrder = {"fieldName"})
@Deprecated
public class StatisticField implements HasDisplayValue, Comparable<StatisticField>, SharedObject {

    private static final long serialVersionUID = 8967939276508418808L;

    @XmlElement(name = "fieldName")
    private String fieldName;

    public StatisticField() {
        // Default constructor necessary for GWT serialisation.
    }

    public StatisticField(final String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(final String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public int compareTo(final StatisticField o) {
        return fieldName.compareToIgnoreCase(o.fieldName);
    }

    @Override
    public String getDisplayValue() {
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fieldName == null)
                ? 0
                : fieldName.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StatisticField other = (StatisticField) obj;
        if (fieldName == null) {
            return other.fieldName == null;
        } else return fieldName.equals(other.fieldName);
    }

    @Override
    public String toString() {
        return "StatisticField [fieldName=" + fieldName + "]";
    }

    public StatisticField deepCopy() {
        return new StatisticField(fieldName);
    }

}
