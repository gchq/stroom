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

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDoubleListReference {

    private final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
    private final DoubleListReference reference = valueReferenceIndex.addDoubleList("test");

    @Test
    void numbers() {
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        assertThat(reference.hasDuration(storedValues)).isFalse();
        reference.set(storedValues, new ArrayList<>(List.of(1.5, 2.0)));

        final StoredValues read = read(write(storedValues));
        assertThat(reference.get(read)).containsExactly(1.5, 2.0);
        assertThat(reference.hasDuration(read)).isFalse();
    }

    @Test
    void durations() {
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        reference.set(storedValues, new ArrayList<>(List.of(1.5)));
        reference.setHasDuration(storedValues);
        // As a generator adds another value.
        final List<Double> list = reference.get(storedValues);
        list.add(2.0);
        reference.set(storedValues, list);
        reference.setHasDuration(storedValues);
        // Not copied for every value.
        assertThat(reference.get(storedValues)).isSameAs(list);

        for (final StoredValues values : List.of(storedValues, read(write(storedValues)))) {
            assertThat(reference.get(values)).containsExactly(1.5, 2.0);
            assertThat(reference.hasDuration(values)).isTrue();
        }
    }

    // Table builder rules keep what they have written, so this must always be readable.
    @Test
    void layout() {
        final ByteBufferOutput output = new ByteBufferOutput(1000);
        final KryoDataWriter writer = new KryoDataWriter(output);
        writer.writeInt(2);
        writer.writeDouble(1.5);
        writer.writeDouble(2.0);

        final StoredValues read = read(output);
        assertThat(reference.get(read)).containsExactly(1.5, 2.0);
        assertThat(reference.hasDuration(read)).isFalse();
        assertThat(write(read).toBytes()).isEqualTo(output.toBytes());
    }

    private ByteBufferOutput write(final StoredValues storedValues) {
        final ByteBufferOutput output = new ByteBufferOutput(1000);
        valueReferenceIndex.write(storedValues, new KryoDataWriter(output));
        return output;
    }

    private StoredValues read(final ByteBufferOutput output) {
        return valueReferenceIndex.read(new KryoDataReader(new ByteBufferInput(output.toBytes())));
    }
}
