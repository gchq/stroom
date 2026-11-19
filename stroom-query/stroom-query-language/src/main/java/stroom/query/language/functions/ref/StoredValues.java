/*
 * Copyright 2023 Crown Copyright
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
    private long windowKey;
    private int windowShift;

    public StoredValues(final Object[] values) {
        this.values = values;
    }

    public Object get(final int index) {
        return this.values[index];
    }

    public void set(final int index, final Object val) {
        this.values[index] = val;
    }

    /// Gets the key of the time window that the row being added is in, for a query with a `window`
    /// clause. It is held only while a row is added, and is not stored with the values.
    ///
    /// @return The window key, as given by `TimeWindows`.
    public long getWindowKey() {
        return windowKey;
    }

    /// Sets the key of the time window that the row being added is in.
    ///
    /// @param windowKey The window key, as given by `TimeWindows`.
    public void setWindowKey(final long windowKey) {
        this.windowKey = windowKey;
    }

    /// Gets how many windows away the row being added really is from the window it is being added
    /// to. It is not zero for a copy of a row made for a function such as `lag`, which reads the
    /// values of another window. Like the window key it is not stored with the values.
    ///
    /// @return The number of windows, which is zero for a row in its own window, more than zero for a
    ///         row from an earlier window and less than zero for a row from a later one.
    public int getWindowShift() {
        return windowShift;
    }

    /// Sets how many windows away the row being added really is from the window it is being added to.
    ///
    /// @param windowShift The number of windows, as [#getWindowShift()] gives it.
    public void setWindowShift(final int windowShift) {
        this.windowShift = windowShift;
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
        copy.setWindowKey(windowKey);
        copy.setWindowShift(windowShift);
        return copy;
    }
}
