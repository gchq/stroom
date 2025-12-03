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

package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.query.common.v2.LmdbKV;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jakarta.inject.Inject;
import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The serialised key is of the form:
 * <pre>{@code <hash bytes (8)><seq no bytes(variable)>}</pre>
 */
public class DuplicateCheckRowSerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

    private static final int HASH_BYTES = Long.BYTES;
    private static final int MAX_KEY_BYTES = HASH_BYTES + Long.BYTES;

    private final ByteBufferFactory byteBufferFactory;

    @Inject
    public DuplicateCheckRowSerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public LmdbKV createLmdbKV(final DuplicateCheckRow row) {
        final List<String> values = row.getValues();
        final byte[] bytes = serialise(values);
        return createLmdbKV(bytes);
    }

    /**
     * Allow this to be overridden for testing with a simpler hash algo
     */
    protected long createHash(final byte[] bytes) {
        return LongHashFunction.xx3().hashBytes(bytes);
    }

    private LmdbKV createLmdbKV(final byte[] bytes) {
        // Hash the value.
        final long rowHash = createHash(bytes);
        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(MAX_KEY_BYTES);
        keyByteBuffer.putLong(rowHash);
        keyByteBuffer.flip();

        // TODO : Possibly trim bytes, although should have already happened.
        final ByteBuffer valueByteBuffer = byteBufferFactory.acquire(bytes.length);
        valueByteBuffer.put(bytes);
        valueByteBuffer.flip();

        LOGGER.trace(() -> "Created row (" + rowHash + ", " + Arrays.toString(bytes) + ")");
        return new LmdbKV(null, keyByteBuffer, valueByteBuffer);
    }

    public DuplicateCheckRow createDuplicateCheckRow(final ByteBuffer valBuffer) {
        final List<String> values = new ArrayList<>();
        try (final Input input = new ByteBufferInput(valBuffer)) {
            while (!input.end()) {
                final String value = input.readString();
                values.add(value);
            }
        }
        return new DuplicateCheckRow(values);
    }

    private byte[] serialise(final List<String> values) {
        final byte[] bytes;
        try (final Output output = new Output(512, -1)) {
            for (final String value : values) {
                output.writeString(value);
            }
            bytes = output.toBytes();
        }
        return bytes;
    }

    public int getKeyLength() {
        return HASH_BYTES;
    }
}
