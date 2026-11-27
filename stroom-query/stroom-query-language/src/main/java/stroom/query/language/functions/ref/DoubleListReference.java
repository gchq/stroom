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

import java.util.ArrayList;
import java.util.List;

public class DoubleListReference implements ValueReference<List<Double>> {

    private final int index;
    private final String name;

    DoubleListReference(final int index, final String name) {
        this.index = index;
        this.name = name;
    }

    @Override
    public List<Double> get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return new ArrayList<>();
        }
        return (List<Double>) o;
    }

    @Override
    public void set(final StoredValues storedValues, final List<Double> value) {
        storedValues.set(index, value);
    }

    /// Records that at least one of the numbers was a duration.
    ///
    /// @param storedValues The values that have the list, which must have been set.
    public void setHasDuration(final StoredValues storedValues) {
        final List<Double> list = get(storedValues);
        if (!(list instanceof DurationList)) {
            set(storedValues, new DurationList(list));
        }
    }

    /// Tells whether at least one of the numbers was a duration.
    ///
    /// @param storedValues The values that have the list.
    /// @return True if one was.
    public boolean hasDuration(final StoredValues storedValues) {
        return get(storedValues) instanceof DurationList;
    }

    @Override
    public void read(final StoredValues storedValues, final DataReader reader) {
        // The length is negative if there was a duration. Table builder rules keep what is written here, so it must
        // always be possible to read what was written before there was such a thing.
        final int length = reader.readInt();
        final List<Double> list = new ArrayList<>(Math.abs(length));
        for (int i = 0; i < Math.abs(length); i++) {
            list.add(reader.readDouble());
        }
        set(storedValues, list);
        if (length < 0) {
            setHasDuration(storedValues);
        }
    }

    @Override
    public void write(final StoredValues storedValues, final DataWriter writer) {
        final List<Double> list = get(storedValues);
        writer.writeInt(hasDuration(storedValues)
                ? -list.size()
                : list.size());
        for (final Double d : list) {
            writer.writeDouble(d);
        }
    }

    @Override
    public String toString() {
        return name;
    }


    // --------------------------------------------------------------------------------


    // A list that at least one duration has been added to.
    private static final class DurationList extends ArrayList<Double> {

        private DurationList(final List<Double> list) {
            super(list);
        }
    }
}
