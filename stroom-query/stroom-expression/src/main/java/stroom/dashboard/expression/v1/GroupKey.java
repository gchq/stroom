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
import java.util.Arrays;
import java.util.Objects;

public final class GroupKey implements Serializable {
    private final int depth;
    private final GroupKey parent;
    private final Val[] values;

    public GroupKey(final int depth,
                    final GroupKey parent,
                    final Val[] values) {
        this.depth = depth;
        this.parent = parent;
        this.values = values;
    }

    public GroupKey(final Val value) {
        this(new Val[]{value});
    }

    public GroupKey(final Val[] values) {
        this.depth = 0;
        this.parent = null;
        this.values = values;
    }

    public GroupKey(final GroupKey parent, final Val value) {
        this(parent, new Val[]{value});
    }

    public GroupKey(final GroupKey parent, final Val[] values) {
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
    public Val[] getValues() {
        return values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GroupKey groupKey = (GroupKey) o;
        return depth == groupKey.depth &&
                Objects.equals(parent, groupKey.parent) &&
                Arrays.equals(values, groupKey.values);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(depth, parent);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }

    private void append(final StringBuilder sb) {
        if (parent != null) {
            parent.append(sb);
            sb.append("/");
        }

        if (values != null && values.length > 0) {
            for (final Val val : values) {
                if (val != null) {
                    sb.append(val.toString());
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
