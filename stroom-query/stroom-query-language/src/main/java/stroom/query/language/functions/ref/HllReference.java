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

import stroom.query.language.functions.hll.HyperLogLogPlusPlus;

/// Refers to a stored sketch that the number of unique values is estimated from.
public class HllReference implements ValueReference<HyperLogLogPlusPlus> {

    private final int index;
    private final String name;
    private final int precision;

    /// Create a reference.
    ///
    /// @param index     The position of the sketch in the stored values.
    /// @param name      The name of the function that the sketch is for.
    /// @param precision The precision of the sketch.
    HllReference(final int index, final String name, final int precision) {
        this.index = index;
        this.name = name;
        this.precision = precision;
    }

    /// Get the sketch of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @return The sketch, which is a new empty one if there is none, and must then be set to be kept.
    @Override
    public HyperLogLogPlusPlus get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return new HyperLogLogPlusPlus(precision);
        }
        return (HyperLogLogPlusPlus) o;
    }

    /// Set the sketch of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @param value        The sketch.
    @Override
    public void set(final StoredValues storedValues, final HyperLogLogPlusPlus value) {
        storedValues.set(index, value);
    }

    /// Read the sketch of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @param reader       The reader to read from.
    @Override
    public void read(final StoredValues storedValues, final DataReader reader) {
        set(storedValues, HyperLogLogPlusPlus.read(reader, precision));
    }

    /// Write the sketch of some stored values.
    ///
    /// @param storedValues The stored values.
    /// @param writer       The writer to write to.
    @Override
    public void write(final StoredValues storedValues, final DataWriter writer) {
        get(storedValues).write(writer);
    }

    /// Describe this reference.
    ///
    /// @return The name of the function that the sketch is for.
    @Override
    public String toString() {
        return name;
    }
}
