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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

import java.util.Arrays;

/**
 * Class that represents a hit in the index
 */
public class Row implements SharedObject {
    private static final long serialVersionUID = 4379892306375080112L;

    private String groupKey;
    private SharedObject[] values;
    private int depth;

    public Row() {
        // Default constructor necessary for GWT serialisation.
    }

    public Row(final String groupKey, final SharedObject[] values, final int depth) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public SharedObject[] getValues() {
        return values;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Row)) {
            return false;
        }

        final Row result = (Row) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(groupKey, result.groupKey);
        builder.appendSuper(Arrays.equals(values, result.values));
        builder.append(depth, result.depth);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(groupKey);
        builder.append(Arrays.hashCode(values));
        builder.append(depth);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Object value : values) {
            sb.append(value);
            sb.append("\t");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
}
