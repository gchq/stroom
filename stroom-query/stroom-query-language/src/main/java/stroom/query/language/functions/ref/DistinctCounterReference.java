/*
 * Copyright 2026 Crown Copyright
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

import stroom.query.language.functions.hll.DistinctCounter;

/// Refers to a stored counter of unique values.
public class DistinctCounterReference implements ValueReference<DistinctCounter> {

    private final int index;
    private final String name;
    private final int limit;

    /// Create a reference.
    ///
    /// @param index The position of the counter in the stored values.
    /// @param name  The name of the function that the counter is for.
    /// @param limit The most unique values that are counted exactly.
    DistinctCounterReference(final int index, final String name, final int limit) {
        this.index = index;
        this.name = name;
        this.limit = limit;
    }

    /// Get the counter of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @return The counter, which is a new empty one if there is none, and must then be set to be kept.
    @Override
    public DistinctCounter get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return new DistinctCounter(limit);
        }
        return (DistinctCounter) o;
    }

    /// Set the counter of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @param value        The counter.
    @Override
    public void set(final StoredValues storedValues, final DistinctCounter value) {
        storedValues.set(index, value);
    }

    /// Read the counter of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @param reader       The reader to read from.
    @Override
    public void read(final StoredValues storedValues, final DataReader reader) {
        set(storedValues, DistinctCounter.read(reader, limit));
    }

    /// Write the counter of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @param writer       The writer to write to.
    @Override
    public void write(final StoredValues storedValues, final DataWriter writer) {
        get(storedValues).write(writer);
    }

    /// Describe this reference.
    ///
    /// @return The name of the function that the counter is for.
    @Override
    public String toString() {
        return name;
    }
}
