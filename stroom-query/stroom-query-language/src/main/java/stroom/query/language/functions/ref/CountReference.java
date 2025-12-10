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

public class CountReference implements ValueReference<Long> {

    private final int index;
    private final String name;

    CountReference(final int index, final String name) {
        this.index = index;
        this.name = name;
    }

    public void increment(final StoredValues storedValues) {
        add(storedValues, 1);
    }

    public void add(final StoredValues storedValues, final long diff) {
        set(storedValues, get(storedValues) + diff);
    }

    @Override
    public Long get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return 0L;
        }
        return (long) o;
    }

    @Override
    public void set(final StoredValues storedValues, final Long value) {
        storedValues.set(index, value);
    }

    @Override
    public void read(final StoredValues storedValues, final DataReader reader) {
        set(storedValues, reader.readLong());
    }

    @Override
    public void write(final StoredValues storedValues, final DataWriter writer) {
        writer.writeLong(get(storedValues));
    }

    @Override
    public String toString() {
        return name;
    }
}
