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

package stroom.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"fields", "limit"})
@JsonInclude(Include.NON_EMPTY)
@XmlType(name = "VisValues", propOrder = {"fields", "limit"})
public class VisValues implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private VisField[] fields;
    private VisLimit limit;

    public VisValues() {
    }

    public VisValues(final VisField[] fields, final VisLimit limit) {
        this.fields = fields;
        this.limit = limit;
    }

    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    public VisField[] getFields() {
        return fields;
    }

    public void setFields(final VisField[] fields) {
        this.fields = fields;
    }

    @XmlElement
    public VisLimit getLimit() {
        return limit;
    }

    public void setLimit(final VisLimit limit) {
        this.limit = limit;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof VisValues)) return false;

        final VisValues visValues = (VisValues) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(fields, visValues.fields)) return false;
        return limit != null ? limit.equals(visValues.limit) : visValues.limit == null;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(fields);
        result = 31 * result + (limit != null ? limit.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VisValues{" +
                "fields=" + Arrays.toString(fields) +
                ", limit=" + limit +
                '}';
    }
}