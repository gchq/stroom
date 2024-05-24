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

class DuplicateCheckRowSerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

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


    private LmdbKV createLmdbKV(final byte[] bytes) {
        // Hash the value.
        final long rowHash = LongHashFunction.xx3().hashBytes(bytes);

        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(Long.BYTES);
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
            if (!input.end()) {
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
}
