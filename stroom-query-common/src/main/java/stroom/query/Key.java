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

package stroom.query;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;
import java.util.Collections;
import java.util.List;

@JsonPropertyOrder({"parent", "values"})
@XmlType(name = "Key", propOrder = {"parent", "values"})
public class Key {
    private final int depth;
    private final Key parent;
    private final List<Object> values;

    public Key(final Object value) {
        this(Collections.singletonList(value));
    }

    public Key(final List<Object> values) {
        this.depth = 0;
        this.parent = null;
        this.values = values;
    }

    public Key(final Key parent, final Object value) {
        this(parent, Collections.singletonList(value));
    }

    public Key(final Key parent, final List<Object> values) {
        if (parent != null) {
            this.depth = parent.depth + 1;
        } else {
            this.depth = 0;
        }
        this.parent = parent;
        this.values = values;
    }

    public int getDepth() {
        return depth;
    }

    @XmlElement
    public Key getParent() {
        return parent;
    }

    @XmlElementWrapper(name = "values")
    @XmlElements({
            @XmlElement(name = "byte", type = Byte.class),
            @XmlElement(name = "double", type = Double.class),
            @XmlElement(name = "float", type = Float.class),
            @XmlElement(name = "short", type = Short.class),
            @XmlElement(name = "integer", type = Integer.class),
            @XmlElement(name = "long", type = Long.class),
            @XmlElement(name = "string", type = String.class)
    })
    public List<Object> getValues() {
        return values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Key key = (Key) o;

        if (depth != key.depth) return false;
        if (parent != null ? !parent.equals(key.parent) : key.parent != null) return false;
        return values != null ? values.equals(key.values) : key.values == null;
    }

    @Override
    public int hashCode() {
        int result = depth;
        result = 31 * result + (parent != null ? parent.hashCode() : 0);
        result = 31 * result + (values != null ? values.hashCode() : 0);
        return result;
    }

    private void appendString(final StringBuilder sb) {
        if (parent != null) {
            parent.appendString(sb);
            sb.append("/");
        }

        if (values != null && values.size() > 0) {
            for (final Object o : values) {
                if (o != null) {
                    sb.append(o.toString());
                }
                sb.append("|");
            }
            sb.setLength(sb.length() - 1);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendString(sb);
        return sb.toString();
    }
}
