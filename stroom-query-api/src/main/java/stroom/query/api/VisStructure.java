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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"nest", "values"})
@XmlType(name = "visStructure", propOrder = {"nest", "values"})
public class VisStructure implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private VisNest nest;
    private VisValues values;

    public VisStructure() {
    }

    public VisStructure(final VisNest nest, final VisValues values) {
        this.nest = nest;
        this.values = values;
    }

    @XmlElement
    public VisNest getNest() {
        return nest;
    }

    public void setNest(final VisNest nest) {
        this.nest = nest;
    }

    @XmlElement
    public VisValues getValues() {
        return values;
    }

    public void setValues(final VisValues values) {
        this.values = values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof VisStructure)) return false;

        final VisStructure that = (VisStructure) o;

        if (nest != null ? !nest.equals(that.nest) : that.nest != null) return false;
        return values != null ? values.equals(that.values) : that.values == null;
    }

    @Override
    public int hashCode() {
        int result = nest != null ? nest.hashCode() : 0;
        result = 31 * result + (values != null ? values.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VisStructure{" +
                "nest=" + nest +
                ", values=" + values +
                '}';
    }
}