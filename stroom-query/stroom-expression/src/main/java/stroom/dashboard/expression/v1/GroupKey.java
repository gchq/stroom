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

package stroom.dashboard.expression.v1;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GroupKey implements Key, Serializable {
    private final int depth;
    private final GroupKey parent;
    private final List<Val> values;

    public GroupKey(final Val value) {
        this(Collections.singletonList(value));
    }

    public GroupKey(final List<Val> values) {
        this.depth = 0;
        this.parent = null;
        this.values = values;
    }

    public GroupKey(final GroupKey parent, final Val value) {
        this(parent, Collections.singletonList(value));
    }

    public GroupKey(final GroupKey parent, final List<Val> values) {
        if (parent != null) {
            this.depth = parent.getDepth() + 1;
        } else {
            this.depth = 0;
        }
        this.parent = parent;
        this.values = values;
    }

    public int getDepth() {
        return depth;
    }

    public GroupKey getParent() {
        return parent;
    }

    @XmlElementWrapper(name = "values")
    @XmlElements({
            @XmlElement(name = "boolean", type = ValBoolean.class),
            @XmlElement(name = "double", type = ValDouble.class),
            @XmlElement(name = "integer", type = ValInteger.class),
            @XmlElement(name = "long", type = ValLong.class),
            @XmlElement(name = "string", type = ValString.class),
            @XmlElement(name = "err", type = ValErr.class),
            @XmlElement(name = "null", type = ValNull.class)
    })
    public List<Val> getValues() {
        return values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GroupKey key = (GroupKey) o;
        return depth == key.depth &&
                Objects.equals(parent, key.parent) &&
                Objects.equals(values, key.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depth, parent, values);
    }

    private void append(final StringBuilder sb) {
        if (parent != null) {
            parent.append(sb);
            sb.append("/");
        }

        if (values != null && values.size() > 0) {
            for (final Val o : values) {
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
        append(sb);
        return sb.toString();
    }
}
