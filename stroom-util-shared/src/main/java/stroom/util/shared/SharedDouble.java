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

public class SharedDouble extends Number implements SharedObject, Comparable<SharedDouble> {
    private static final long serialVersionUID = -8596914617295376525L;

    private Double _double;

    public SharedDouble() {
        // Default constructor necessary for GWT serialisation.
    }

    public SharedDouble(final Double _double) {
        this._double = _double;
    }

    public Double getDouble() {
        return _double;
    }

    public void setDouble(final Double _double) {
        this._double = _double;
    }

    @Override
    public int intValue() {
        return _double.intValue();
    }

    @Override
    public long longValue() {
        return _double.longValue();
    }

    @Override
    public float floatValue() {
        return _double.floatValue();
    }

    @Override
    public double doubleValue() {
        return _double.doubleValue();
    }

    @Override
    public int hashCode() {
        return _double.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof SharedDouble) {
            return ((SharedDouble) obj)._double.equals(_double);
        }

        return false;
    }

    @Override
    public int compareTo(final SharedDouble sharedDouble) {
        return _double.compareTo(sharedDouble._double);
    }

    @Override
    public String toString() {
        return String.valueOf(_double);
    }
}
