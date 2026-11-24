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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public class ValueReferenceIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueReferenceIndex.class);

    private final List<ValueReference<?>> list = new ArrayList<>();
    private final SortedSet<Integer> windowShifts = new TreeSet<>();
    private boolean usesWindows;
    private final WindowRange windowRange = new WindowRange();

    /// Gets the range of time windows that hold real rows, which the functions that read other
    /// windows are given and a result store sets.
    ///
    /// @return The range, which is shared by everything compiled with this index.
    public WindowRange getWindowRange() {
        return windowRange;
    }

    /// Records that a function reads the values of another time window, so that rows are copied
    /// to the window that reads them.
    ///
    /// @param windowShift How many windows after its own a row is copied to, which is less than zero
    ///                    for a window before.
    public void addWindowShift(final int windowShift) {
        windowShifts.add(windowShift);
    }

    /// Gets the distances that rows must be copied for the functions that read other time windows.
    ///
    /// @return The distinct distances, in windows, none of them zero.
    public int[] getWindowShifts() {
        return windowShifts.stream().mapToInt(Integer::intValue).toArray();
    }

    /// Records that a function works with the time windows of a query.
    public void setUsesWindows() {
        usesWindows = true;
    }

    /// Gets whether any function works with the time windows of a query.
    ///
    /// @return True if one does.
    public boolean usesWindows() {
        return usesWindows;
    }

    public CountReference addCount(final String name) {
        return add(new CountReference(list.size(), name));
    }

    public DoubleListReference addDoubleList(final String name) {
        return add(new DoubleListReference(list.size(), name));
    }

    public StringListReference addStringList(final String name) {
        return add(new StringListReference(list.size(), name));
    }

    public ValListReference addValList(final String name) {
        return add(new ValListReference(list.size(), name));
    }

    public FieldValReference addFieldValue(final String name, final int fieldIndex) {
        final FieldValReference valueReference =
                new FieldValReference(list.size(), fieldIndex, name);

        // Only store one reference to each field.
        final int index = list.indexOf(valueReference);
        if (index != -1) {
            return (FieldValReference) list.get(index);
        }

        list.add(valueReference);
        return valueReference;
    }

    public RandomValReference addRandomValue(final String name) {
        return add(new RandomValReference(list.size(), name));
    }

    public ValReference addValue(final String name) {
        return add(new ValReference(list.size(), name));
    }

    public Integer getFieldValIndex(final String name) {
        for (int index = 0; index < list.size(); index++) {
            final ValueReference<?> valueReference = list.get(index);
            if (valueReference instanceof FieldValReference) {
                if (Objects.equals(valueReference.toString(), name)) {
                    return index;
                }
            }
        }
        return null;
    }

    private <T extends ValueReference<?>> T add(final T valueReference) {
        list.add(valueReference);
        return valueReference;
    }

    public StoredValues createStoredValues() {
        return new StoredValues(new Object[list.size()]);
    }

    public StoredValues read(final DataReader reader) {
        try {
            final StoredValues storedValues = createStoredValues();
            for (final ValueReference<?> valueReference : list) {
                valueReference.read(storedValues, reader);
            }
            return storedValues;
        } catch (final RuntimeException e) {
            final String sb = "Error reading value:\n" +
                              e.getClass().getSimpleName() +
                              "\n" +
                              e.getMessage() +
                              "\n" +
                              "Byte Buffer:\n" +
                              reader.toString() +
                              "\n" +
                              "Value Reference Index:\n" +
                              this;
            LOGGER.error(sb, e);

            throw e;
        }
    }

    public void write(final StoredValues storedValues, final DataWriter writer) {
        try {
            for (final ValueReference<?> valueReference : list) {
                valueReference.write(storedValues, writer);
            }
        } catch (final RuntimeException e) {
            final String sb = "Error writing value:\n" +
                              e.getClass().getSimpleName() +
                              "\n" +
                              e.getMessage() +
                              "\n" +
                              "Value Reference Index:\n" +
                              this;
            LOGGER.error(sb, e);

            throw e;
        }
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
