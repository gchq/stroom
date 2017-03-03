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

package stroom.dashboard.shared;

import stroom.util.shared.SharedObject;

import java.util.List;

/**
 * Class that represents a hit in the index
 */
public class Row implements SharedObject {
    private static final long serialVersionUID = 4379892306375080112L;

    private String groupKey;
    private List<String> values;
    private int depth;

    public Row() {
        // Default constructor necessary for GWT serialisation.
    }

    public Row(final String groupKey, final List<String> values, final int depth) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public List<String> getValues() {
        return values;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Row row = (Row) o;

        if (depth != row.depth) return false;
        if (groupKey != null ? !groupKey.equals(row.groupKey) : row.groupKey != null) return false;
        return values != null ? values.equals(row.values) : row.values == null;
    }

    @Override
    public int hashCode() {
        int result = groupKey != null ? groupKey.hashCode() : 0;
        result = 31 * result + (values != null ? values.hashCode() : 0);
        result = 31 * result + depth;
        return result;
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
