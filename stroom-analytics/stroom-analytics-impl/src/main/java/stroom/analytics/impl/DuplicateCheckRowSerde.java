package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
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
 * <pre>{@code <hash bytes (8)><seq no bytes(variable)>}</pre>
 */
public class DuplicateCheckRowSerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

    private static final int HASH_BYTES = Long.BYTES;
    private static final int SEQUENCE_NUMBER_OFFSET = HASH_BYTES;
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

    /**
     * Extracts the sequence number value from the keyBuffer.
     * Absolute, no flip required
     */
    public long extractSequenceNumber(final ByteBuffer keyBuffer) {
        final int len = keyBuffer.limit() - SEQUENCE_NUMBER_OFFSET;
        if (len == 0) {
            return 0;
        }
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.ofLength(len);
        return unsignedBytes.get(keyBuffer, SEQUENCE_NUMBER_OFFSET);
    }

    /**
     * Updates the keyBuffer with the provided sequence number value.
     * Absolute, no flip required.
     */
    public void setSequenceNumber(final ByteBuffer keyBuffer, final long sequenceNumber) {
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.forValue(sequenceNumber);
        keyBuffer.limit(SEQUENCE_NUMBER_OFFSET + unsignedBytes.length());
        unsignedBytes.put(keyBuffer, SEQUENCE_NUMBER_OFFSET, sequenceNumber);
    }

    /**
     * Create a {@link KeyRange} for all possible keys that share the same hash value
     * as lmdbKV.
     */
    public KeyRange<ByteBuffer> createSequenceKeyRange(final LmdbKV lmdbKV) {
        final long hash = lmdbKV.getRowKey().getLong(0);

        final ByteBuffer startKeyBuf = byteBufferFactory.acquire(HASH_BYTES);
        final ByteBuffer endKeyBuf = byteBufferFactory.acquire(HASH_BYTES);

        // Make the start key (excl)
        startKeyBuf.putLong(hash);
        startKeyBuf.flip();

        // Make the end key (excl)
        endKeyBuf.putLong(hash + 1);
        endKeyBuf.flip();
        return KeyRange.open(startKeyBuf, endKeyBuf);
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
}
