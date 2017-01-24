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

@JsonPropertyOrder({"key", "limit", "nest", "values"})
@XmlType(name = "visNest", propOrder = {"key", "limit", "nest", "values"})
public class VisNest implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private VisField key;
    private VisLimit limit;
    private VisNest nest;
    private VisValues values;

    public VisNest() {
    }

    public VisNest(final VisField key) {
        this.key = key;
    }

    public VisNest(final VisField key, final VisLimit limit, final VisNest nest, final VisValues values) {
        this.key = key;
        this.limit = limit;
        this.nest = nest;
        this.values = values;
    }

    @XmlElement
    public VisField getKey() {
        return key;
    }

    public void setKey(final VisField key) {
        this.key = key;
    }

    @XmlElement
    public VisLimit getLimit() {
        return limit;
    }

    public void setLimit(final VisLimit limit) {
        this.limit = limit;
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
        if (!(o instanceof VisNest)) return false;

        final VisNest visNest = (VisNest) o;

        if (key != null ? !key.equals(visNest.key) : visNest.key != null) return false;
        if (limit != null ? !limit.equals(visNest.limit) : visNest.limit != null) return false;
        if (nest != null ? !nest.equals(visNest.nest) : visNest.nest != null) return false;
        return values != null ? values.equals(visNest.values) : visNest.values == null;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (limit != null ? limit.hashCode() : 0);
        result = 31 * result + (nest != null ? nest.hashCode() : 0);
        result = 31 * result + (values != null ? values.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VisNest{" +
                "key=" + key +
                ", limit=" + limit +
                ", nest=" + nest +
                ", values=" + values +
                '}';
    }
}