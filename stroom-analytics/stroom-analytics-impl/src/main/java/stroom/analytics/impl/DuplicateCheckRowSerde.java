package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.query.common.v2.LmdbKV;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jakarta.inject.Inject;
import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The serialised key is of the form:
 * <pre>{@code <hash bytes (8)><seq no bytes(2)>}</pre>
 */
class DuplicateCheckRowSerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

    // Two bytes gives us 65k possible hash clashes, per hash. Hopefully enough
    private static final int SEQUENCE_NUMBER_BYTES = 2;
    private static final int HASH_OFFSET = 0;
    private static final int HASH_BYTES = Long.BYTES;
    private static final int SEQUENCE_NUMBER_OFFSET = HASH_BYTES;
    private static final int KEY_BYTES = HASH_BYTES + SEQUENCE_NUMBER_BYTES;
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(SEQUENCE_NUMBER_BYTES);

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
        final ByteBuffer keyByteBuffer = acquireKeyBuffer();
        keyByteBuffer.putLong(rowHash);
        UNSIGNED_BYTES.put(keyByteBuffer, 0);
        keyByteBuffer.flip();

        // TODO : Possibly trim bytes, although should have already happened.
        final ByteBuffer valueByteBuffer = byteBufferFactory.acquire(bytes.length);
        valueByteBuffer.put(bytes);
        valueByteBuffer.flip();

        LOGGER.trace(() -> "Created row (" + rowHash + ", " + Arrays.toString(bytes) + ")");
        return new LmdbKV(null, keyByteBuffer, valueByteBuffer);
    }

    /**
     * Copy the hash part of the sourceKeyBuffer into destKeyBuffer.
     * Absolute, no flip needed.
     */
    private void copyHashPart(final ByteBuffer sourceKeyBuffer,
                              final ByteBuffer destKeyBuffer) {
        ByteBufferUtils.copy(sourceKeyBuffer, destKeyBuffer, HASH_OFFSET, HASH_OFFSET, HASH_BYTES);
    }

    /**
     * Extracts the sequence number value from the keyBuffer.
     * Absolute, no flip required
     */
    public long extractSequenceNumber(final ByteBuffer keyBuffer) {
        return UNSIGNED_BYTES.get(keyBuffer, SEQUENCE_NUMBER_OFFSET);
    }

    /**
     * Updates the keyBuffer with the provided sequence number value.
     * Absolute, no flip required.
     */
    public void setSequenceNumber(final ByteBuffer keyBuffer, final long sequenceNumber) {
        UNSIGNED_BYTES.put(keyBuffer, SEQUENCE_NUMBER_OFFSET, sequenceNumber);
    }

    /**
     * Create a {@link KeyRange} for all possible keys that share the same hash value
     * as lmdbKV.
     */
    public KeyRange<ByteBuffer> createSingleHashKeyRange(final LmdbKV lmdbKV) {
        final ByteBuffer startKeyBuf = acquireKeyBuffer();
        final ByteBuffer endKeyBuf = acquireKeyBuffer();
        acquireKeyBuffer();
        // Make the start key (inc)
        copyHashPart(lmdbKV.getRowKey(), startKeyBuf);
        startKeyBuf.position(HASH_BYTES);
        UNSIGNED_BYTES.put(startKeyBuf, 0);
        startKeyBuf.flip();

        // Make the end key (inc)
        copyHashPart(lmdbKV.getRowKey(), endKeyBuf);
        endKeyBuf.position(HASH_BYTES);
        UNSIGNED_BYTES.put(endKeyBuf, UNSIGNED_BYTES.maxValue());
        endKeyBuf.flip();

        return KeyRange.closed(startKeyBuf, endKeyBuf);
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

    public ByteBuffer acquireKeyBuffer() {
        return byteBufferFactory.acquire(KEY_BYTES);
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
