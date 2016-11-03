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

package stroom.util.shared;

public class SharedLong extends Number implements SharedObject, Comparable<SharedLong> {
    private static final long serialVersionUID = 2999109513859666073L;

    private Long _long;

    public SharedLong() {
        // Default constructor necessary for GWT serialisation.
    }

    public SharedLong(final Long _long) {
        this._long = _long;
    }

    public Long getLong() {
        return _long;
    }

    public void setLong(final Long _long) {
        this._long = _long;
    }

    @Override
    public int intValue() {
        return _long.intValue();
    }

    @Override
    public long longValue() {
        return _long.longValue();
    }

    @Override
    public float floatValue() {
        return _long.floatValue();
    }

    @Override
    public double doubleValue() {
        return _long.doubleValue();
    }

    @Override
    public int hashCode() {
        return _long.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof SharedLong) {
            return ((SharedLong) obj)._long.equals(_long);
        }

        return false;
    }

    @Override
    public int compareTo(final SharedLong sharedLong) {
        return _long.compareTo(sharedLong._long);
    }

    @Override
    public String toString() {
        return String.valueOf(_long);
    }
}
