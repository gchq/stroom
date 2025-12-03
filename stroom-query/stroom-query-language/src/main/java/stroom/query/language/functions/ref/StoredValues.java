/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.language.functions.ref;

import java.util.Arrays;

public class StoredValues {

    private final Object[] values;
    private int period;

    public StoredValues(final Object[] values) {
        this.values = values;
    }

    public Object get(final int index) {
        return this.values[index];
    }

    public void set(final int index, final Object val) {
        this.values[index] = val;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(final int period) {
        this.period = period;
    }

    @Override
    public String toString() {
        return "StoredValues{" +
               "values=" + Arrays.toString(this.values) +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StoredValues that = (StoredValues) o;
        return Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    public StoredValues copy() {
        final Object[] arr = new Object[values.length];
        System.arraycopy(values, 0, arr, 0, values.length);
        final StoredValues copy = new StoredValues(arr);
        copy.setPeriod(period);
        return copy;
    }
}
