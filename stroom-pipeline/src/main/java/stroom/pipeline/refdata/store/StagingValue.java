package stroom.pipeline.refdata.store;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 */
public interface StagingValue extends RefDataValue {

    int TYPE_ID_OFFSET = 0;
    int VALUE_HASH_OFFSET = TYPE_ID_OFFSET + Integer.BYTES;
    int VALUE_OFFSET = VALUE_HASH_OFFSET + Long.BYTES;
    int META_LENGTH = Integer.BYTES + Long.BYTES;

    /**
     * @return The hash of the value
     */
    long getValueHashCode();

    /**
     * @return The type of the value
     */
    int getTypeId();

    /**
     * @return The size in bytes
     */
    int size();

    /**
     * @return A buffer containing just the value part
     */
    ByteBuffer getValueBuffer();

    /**
     * @return The whole buffer containing type ID, value hash and value
     */
    ByteBuffer getFullByteBuffer();

    /**
     * Copies the contents of this into a new {@link StagingValue} backed by the supplied buffer.
     * Only call this after you have finished writing to the output stream.
     */
    StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier);
}
