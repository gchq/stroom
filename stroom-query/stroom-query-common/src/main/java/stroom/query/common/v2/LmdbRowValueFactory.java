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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.query.language.functions.ref.DataWriter;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class LmdbRowValueFactory {

    private final ByteBufferFactory byteBufferFactory;
    private final ValueReferenceIndex valueReferenceIndex;
    private final DataWriterFactory writerFactory;

    private static final int MIN_BUFFER_SIZE = 128;

    // The size that recent new rows have needed. Merged rows can be far larger so are not included, as there can be a
    // great many new rows waiting to be stored. This can be changed by many threads at once, but it is only a guess.
    private int newRowBufferSize = MIN_BUFFER_SIZE;

    public LmdbRowValueFactory(final ByteBufferFactory byteBufferFactory,
                               final ValueReferenceIndex valueReferenceIndex,
                               final DataWriterFactory writerFactory) {
        this.byteBufferFactory = byteBufferFactory;
        this.valueReferenceIndex = valueReferenceIndex;
        this.writerFactory = writerFactory;
    }

    /// Creates a row value from what is written to an output. If writing fails then there is nothing to release.
    ///
    /// @param expectedSize How many bytes are expected to be written, though more can be.
    /// @param consumer     Writes to the output.
    /// @return The row value, which must be released.
    public ByteBuffer useOutput(final int expectedSize, final Consumer<ByteBufferPoolOutput> consumer) {
        try (final ByteBufferPoolOutput output = new ByteBufferPoolOutput(
                byteBufferFactory, Math.max(MIN_BUFFER_SIZE, expectedSize), -1)) {
            try {
                consumer.accept(output);
                return output.getByteBuffer().flip();
            } catch (final RuntimeException | Error e) {
                byteBufferFactory.release(output.getByteBuffer());
                throw e;
            }
        }
    }

    /// Gets how many bytes to expect two row values to take once they are merged.
    ///
    /// @param existingSize The size of the existing row value.
    /// @param newSize      The size of the new row value.
    /// @return The expected size.
    static int getMergedSize(final int existingSize, final int newSize) {
        // The merged row is about as big as the two rows at most, but can be no bigger than one of them. A buffer that
        // is too large to be pooled is costly, so don't use one on the chance that it is needed.
        final int largest = Math.max(existingSize, newSize);
        final int total = existingSize + newSize;
        return largest <= ByteBufferFactoryImpl.MAX_CACHED_BUFFER_SIZE
                ? Math.min(total, ByteBufferFactoryImpl.MAX_CACHED_BUFFER_SIZE)
                : total;
    }

    public ByteBuffer create(final StoredValues storedValues) {
        final ByteBuffer byteBuffer = useOutput(newRowBufferSize, output -> {
            try (final KryoDataWriter writer = writerFactory.create(output)) {
                write(storedValues, writer);
            }
        });
        // Round up, as rows vary in size, and come back down over a few rows after a large one.
        final int size = Integer.highestOneBit(byteBuffer.remaining()) << 1;
        newRowBufferSize = Math.max(MIN_BUFFER_SIZE, Math.max(size, newRowBufferSize / 2));
        return byteBuffer;
    }

    private void write(final StoredValues storedValues,
                       final DataWriter writer) {
        valueReferenceIndex.write(storedValues, writer);
    }
}
