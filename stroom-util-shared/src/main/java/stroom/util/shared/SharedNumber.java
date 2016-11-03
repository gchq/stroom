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

public class SharedNumber extends Number implements SharedObject {
    private static final long serialVersionUID = -6502263322370395720L;

    private Number _number;

    public SharedNumber() {
        // Default constructor necessary for GWT serialisation.
    }

    private SharedNumber(final Number _number) {
        this._number = _number;
    }

    public static SharedNumber wrap(final Number _number) {
        if (_number == null) {
            return null;
        }
        return new SharedNumber(_number);
    }

    @Override
    public int intValue() {
        return _number.intValue();
    }

    @Override
    public long longValue() {
        return _number.longValue();
    }

    @Override
    public float floatValue() {
        return _number.floatValue();
    }

    @Override
    public double doubleValue() {
        return _number.doubleValue();
    }

    @Override
    public int hashCode() {
        return _number.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof SharedNumber) {
            return ((SharedNumber) obj)._number.equals(_number);
        }

        if (obj instanceof Number) {
            return obj.equals(_number);
        }

        return false;
    }

    @Override
    public String toString() {
        return String.valueOf(_number);
    }
}
