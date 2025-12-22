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

    private int bufferSize = 128;

    public LmdbRowValueFactory(final ByteBufferFactory byteBufferFactory,
                               final ValueReferenceIndex valueReferenceIndex,
                               final DataWriterFactory writerFactory) {
        this.byteBufferFactory = byteBufferFactory;
        this.valueReferenceIndex = valueReferenceIndex;
        this.writerFactory = writerFactory;
    }

    public ByteBuffer useOutput(final Consumer<ByteBufferPoolOutput> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, bufferSize, -1)) {
            consumer.accept(output);
            final ByteBuffer byteBuffer = output.getByteBuffer().flip();
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
            return byteBuffer;
        }
    }

    public ByteBuffer create(final StoredValues storedValues) {
        return useOutput(output -> {
            try (final KryoDataWriter writer = writerFactory.create(output)) {
                write(storedValues, writer);
            }
        });
    }

    private void write(final StoredValues storedValues,
                       final DataWriter writer) {
        valueReferenceIndex.write(storedValues, writer);
    }
}
