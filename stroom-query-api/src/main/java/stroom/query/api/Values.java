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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"values"})
@XmlType(name = "Values", propOrder = {"values"})
public class Values implements Serializable {
    private static final long serialVersionUID = 3826654996795750099L;

    private Object[] values;

    public Values() {
    }

    public Values(final Object[] values) {
        this.values = values;
    }

    @XmlElements({
            @XmlElement(name = "byte", type = Byte.class),
            @XmlElement(name = "double", type = Double.class),
            @XmlElement(name = "float", type = Float.class),
            @XmlElement(name = "short", type = Short.class),
            @XmlElement(name = "integer", type = Integer.class),
            @XmlElement(name = "long", type = Long.class),
            @XmlElement(name = "string", type = String.class)
    })
    public Object[] getValues() {
        return values;
    }

    public void setValues(final Object[] values) {
        this.values = values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Values values1 = (Values) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(values, values1.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}