package stroom.pipeline.refdata.store;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.bytebuffer.PooledByteBufferOutputStream.Factory;
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 * <p>
 * Allows us to build a {@link ByteBuffer} backed value from a stream of data and at the end
 * insert the type information and a hash of the value into the buffer. {@link PooledByteBufferOutputStream}
 * is used to expand to accommodate any size of data.
 */
public class StagingValueOutputStream
        extends OutputStream
        implements StagingValue, RefDataValue, AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StagingValueOutputStream.class);

    private static final int BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY = 2_000;

    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    private final PooledByteBufferOutputStream pooledByteBufferOutputStream;

    private Integer typeId = null;
    private ByteBuffer fullBuffer = null;
    private ByteBuffer valueBuffer = null;

    @Inject
    public StagingValueOutputStream(final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                                    final Factory pooledByteBufferOutputStreamFactory) {
        this.valueStoreHashAlgorithm = Objects.requireNonNull(valueStoreHashAlgorithm);
        this.pooledByteBufferOutputStream = Objects.requireNonNull(pooledByteBufferOutputStreamFactory).create(
                BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY);

        writeMetaDataPadding();
    }

    private void writeMetaDataPadding() {
        // Pad out the stream with zero value byte[], so we can write the typeId and hash
        // in later when they are known
        final byte[] paddingBytes = new byte[META_LENGTH];
        try {
            pooledByteBufferOutputStream.write(paddingBytes);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error padding out pooledByteBufferOutputStream: {}",
                    e.getMessage()), e);
        }
    }

    public void setTypeId(final int typeId) {
        this.typeId = typeId;
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        pooledByteBufferOutputStream.write(b, off, len);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        pooledByteBufferOutputStream.write(b);
    }

    @Override
    public void write(final int b) throws IOException {
        pooledByteBufferOutputStream.write(b);
    }

    public void write(final ByteBuffer byteBuffer) throws IOException {
        pooledByteBufferOutputStream.write(byteBuffer);
    }

    public void write(final String value) throws IOException {
        if (!NullSafe.isBlankString(value)) {
            pooledByteBufferOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void release() {
        pooledByteBufferOutputStream.release();
    }

    public void clear() {
        pooledByteBufferOutputStream.clear();
        writeMetaDataPadding();
        typeId = null;
        fullBuffer = null;
        valueBuffer = null;
    }

    /**
     * Only call this after you have finished writing to the output stream.
     */
    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        if (valueStoreHashAlgorithm != null
                && !Objects.equals(this.valueStoreHashAlgorithm, valueStoreHashAlgorithm)) {
            return valueStoreHashAlgorithm.hash(getValueBuffer());
        } else {
            return getValueHashCode();
        }
    }

    /**
     * Only call this after you have finished writing to the output stream.
     */
    @Override
    public long getValueHashCode() {
        if (fullBuffer == null) {
            getValueBuffer();
        }
        return StagingValueSerde.extractValueHash(fullBuffer);
    }

    @Override
    public int getTypeId() {
        checkTypeIdSet();
        return typeId;
    }

    /**
     * Only call this after you have finished writing to the output stream.
     */
    @Override
    public boolean isNullValue() {
        return NullValue.TYPE_ID == getTypeId()
                || getValueBuffer().remaining() == 0;
    }

    @Override
    public void close() {
        pooledByteBufferOutputStream.close();
    }

    public ByteBuffer getValueBuffer() {
        if (valueBuffer == null) {
            getFullByteBuffer();
        }
        return valueBuffer;
    }

    private void checkTypeIdSet() {
        if (typeId == null) {
            throw new RuntimeException("typeId has not been set so we don't know what the bytes are");
        }
    }

    /**
     * Only call this after you have finished writing to the output stream.
     */
    @Override
    public ByteBuffer getFullByteBuffer() {
        if (fullBuffer == null) {
            checkTypeIdSet();
            fullBuffer = pooledByteBufferOutputStream.getPooledByteBuffer().getByteBuffer();
            // Hash just the value part of the buffer
            final int valueLength = fullBuffer.remaining() - META_LENGTH;
            try {
                valueBuffer = fullBuffer.slice(
                        VALUE_OFFSET,
                        fullBuffer.remaining() - META_LENGTH);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message("""
                                Error slicing valueBuffer (offset: {}, length: {}) from fullBuffer: {}
                                byteBuffer: {}""",
                        VALUE_OFFSET,
                        valueLength,
                        e.getMessage(),
                        ByteBufferUtils.byteBufferInfo(fullBuffer)), e);
            }

            LOGGER.trace(() -> LogUtil.message("\nbyteBuffer: {}\nvalueBuffer: {}",
                    ByteBufferUtils.byteBufferInfo(fullBuffer),
                    ByteBufferUtils.byteBufferInfo(valueBuffer)));

            final long valueHash = valueStoreHashAlgorithm.hash(valueBuffer);

            // Now add the type and hash into the buffer without changing its position/limit
            fullBuffer.putInt(TYPE_ID_OFFSET, typeId);
            fullBuffer.putLong(VALUE_HASH_OFFSET, valueHash);
        }
        return fullBuffer;
    }

    /**
     * Only call this after you have finished writing to the output stream.
     *
     * @return The size in bytes
     */
    @Override
    public int size() {
        return fullBuffer.remaining();
    }

    /**
     * Copies the contents of this into a new {@link StagingValue} backed by the supplied buffer.
     * Only call this after you have finished writing to the output stream.
     */
    @Override
    public StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier) {
        final ByteBuffer newByteBuffer = byteBufferSupplier.get();
        ByteBufferUtils.copy(getFullByteBuffer(), newByteBuffer);
        return new StagingValueImpl(newByteBuffer);
    }
}
