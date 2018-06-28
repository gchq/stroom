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

import stroom.docref.SharedObject;

public class SharedInteger extends Number implements SharedObject, Comparable<SharedInteger> {
    private static final long serialVersionUID = -6502263322370395720L;

    private Integer _integer;

    public SharedInteger() {
        // Default constructor necessary for GWT serialisation.
    }

    public SharedInteger(final Integer _integer) {
        this._integer = _integer;
    }

    public Integer getInteger() {
        return _integer;
    }

    public void setInteger(final Integer _integer) {
        this._integer = _integer;
    }

    @Override
    public int intValue() {
        return _integer.intValue();
    }

    @Override
    public long longValue() {
        return _integer.longValue();
    }

    @Override
    public float floatValue() {
        return _integer.floatValue();
    }

    @Override
    public double doubleValue() {
        return _integer.doubleValue();
    }

    @Override
    public int hashCode() {
        return _integer.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof SharedInteger) {
            return ((SharedInteger) obj)._integer.equals(_integer);
        }

        return false;
    }

    @Override
    public int compareTo(final SharedInteger sharedInteger) {
        return _integer.compareTo(sharedInteger._integer);
    }

    @Override
    public String toString() {
        return String.valueOf(_integer);
    }
}
