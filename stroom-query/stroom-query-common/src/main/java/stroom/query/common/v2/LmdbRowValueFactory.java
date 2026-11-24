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

    // The size that new rows have needed. Merged rows can be far larger so are not included, as there can be a great
    // many new rows waiting to be stored.
    private int newRowBufferSize = MIN_BUFFER_SIZE;

    public LmdbRowValueFactory(final ByteBufferFactory byteBufferFactory,
                               final ValueReferenceIndex valueReferenceIndex,
                               final DataWriterFactory writerFactory) {
        this.byteBufferFactory = byteBufferFactory;
        this.valueReferenceIndex = valueReferenceIndex;
        this.writerFactory = writerFactory;
    }

    /// Creates a row value from what is written to an output.
    ///
    /// @param expectedSize How many bytes are expected to be written, though more can be.
    /// @param consumer     Writes to the output.
    /// @return The row value, which must be released.
    public ByteBuffer useOutput(final int expectedSize, final Consumer<ByteBufferPoolOutput> consumer) {
        try (final ByteBufferPoolOutput output = new ByteBufferPoolOutput(
                byteBufferFactory, Math.max(MIN_BUFFER_SIZE, expectedSize), -1)) {
            consumer.accept(output);
            return output.getByteBuffer().flip();
        }
    }

    public ByteBuffer create(final StoredValues storedValues) {
        final ByteBuffer byteBuffer = useOutput(newRowBufferSize, output -> {
            try (final KryoDataWriter writer = writerFactory.create(output)) {
                write(storedValues, writer);
            }
        });
        newRowBufferSize = Math.max(newRowBufferSize, byteBuffer.capacity());
        return byteBuffer;
    }

    private void write(final StoredValues storedValues,
                       final DataWriter writer) {
        valueReferenceIndex.write(storedValues, writer);
    }
}
