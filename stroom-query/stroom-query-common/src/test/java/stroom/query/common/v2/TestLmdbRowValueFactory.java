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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.SimpleByteBufferFactory;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValReference;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLmdbRowValueFactory {

    private static final int LARGE = 100_000;

    private final TrackingByteBufferFactory byteBufferFactory = new TrackingByteBufferFactory();
    private final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
    private final LmdbRowValueFactory lmdbRowValueFactory = new LmdbRowValueFactory(
            byteBufferFactory,
            valueReferenceIndex,
            new DataWriterFactory(new ErrorConsumerImpl(), LARGE));

    // Many new rows can wait to be stored at once, so one large merged row must not make them all large.
    @Test
    void testLargeMergedRowDoesNotMakeNewRowsLarge() {
        valueReferenceIndex.addCount("count");
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();

        final ByteBuffer before = lmdbRowValueFactory.create(storedValues);
        final ByteBuffer merged = lmdbRowValueFactory.useOutput(10, output -> output.write(new byte[LARGE]));
        assertThat(merged.remaining()).isEqualTo(LARGE);

        final ByteBuffer after = lmdbRowValueFactory.create(storedValues);
        assertThat(after.capacity()).isEqualTo(before.capacity()).isLessThan(1000);
        assertThat(after).isEqualTo(before);
        byteBufferFactory.release(before);
        byteBufferFactory.release(merged);
        byteBufferFactory.release(after);
    }

    @Test
    void testExpectedSizeIsUsed() {
        final ByteBuffer merged = lmdbRowValueFactory.useOutput(LARGE, output -> output.write(new byte[LARGE]));
        assertThat(merged.remaining()).isEqualTo(LARGE);
        assertThat(merged.capacity()).isEqualTo(LARGE);
        byteBufferFactory.release(merged);
    }

    // One large new row must not make every row after it large.
    @Test
    void testLargeNewRowDoesNotKeepNewRowsLarge() {
        final ValReference valReference = valueReferenceIndex.addValue("value");
        final int before = createRow(valReference, 10);
        assertThat(before).isLessThan(1000);

        assertThat(createRow(valReference, LARGE)).isGreaterThanOrEqualTo(LARGE);
        int total = 0;
        for (int i = 0; i < 20; i++) {
            total += createRow(valReference, 10);
        }
        assertThat(total).isLessThan(LARGE * 3);
        assertThat(createRow(valReference, 10)).isEqualTo(before);
    }

    // Rows of about the same size should not have to grow their buffers.
    @Test
    void testBufferIsBigEnoughForRowsOfTheSameSize() {
        final ValReference valReference = valueReferenceIndex.addValue("value");
        createRow(valReference, 5_000);
        byteBufferFactory.acquired = 0;
        for (int i = 0; i < 100; i++) {
            createRow(valReference, 5_000 - i);
        }
        assertThat(byteBufferFactory.acquired).isEqualTo(100);
    }

    @Test
    void testBufferIsReleasedIfWritingFails() {
        assertThatThrownBy(() -> lmdbRowValueFactory.useOutput(10, output -> {
            // Make the buffer grow first.
            output.write(new byte[LARGE]);
            throw new IllegalStateException("Unable to write");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to write");
        assertThat(byteBufferFactory.acquired).isGreaterThan(1);
        assertThat(byteBufferFactory.held).isEmpty();
    }

    private int createRow(final ValReference valReference, final int length) {
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        valReference.set(storedValues, ValString.create("x".repeat(length)));
        final ByteBuffer byteBuffer = lmdbRowValueFactory.create(storedValues);
        final int capacity = byteBuffer.capacity();
        byteBufferFactory.release(byteBuffer);
        assertThat(byteBufferFactory.held).isEmpty();
        return capacity;
    }


    // --------------------------------------------------------------------------------


    private static class TrackingByteBufferFactory implements ByteBufferFactory {

        private final ByteBufferFactory delegate = new SimpleByteBufferFactory();
        private final Set<ByteBuffer> held = Collections.newSetFromMap(new IdentityHashMap<>());
        private int acquired;

        @Override
        public ByteBuffer acquire(final int size) {
            final ByteBuffer byteBuffer = delegate.acquire(size);
            held.add(byteBuffer);
            acquired++;
            return byteBuffer;
        }

        @Override
        public void release(final ByteBuffer byteBuffer) {
            assertThat(held.remove(byteBuffer)).isTrue();
            delegate.release(byteBuffer);
        }
    }
}
