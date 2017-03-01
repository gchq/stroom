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

package stroom.query;

import java.io.Serializable;

public class Item implements Serializable {
    private static final long serialVersionUID = 4371018450667741005L;

    Key key;
    Object[] values;
    int depth;

    public Item(final Key key, final Object[] values, final int depth) {
        this.key = key;
        this.values = values;
        this.depth = depth;
    }

    public Key getKey() {
        return key;
    }

    public Object[] getValues() {
        return values;
    }


    public int getDepth() {
        return depth;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;

        final Item item = (Item) o;

        return key != null ? key.equals(item.key) : item.key == null;
    }

    @Override
    public int hashCode() {
        return key != null ? key.hashCode() : 0;
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
