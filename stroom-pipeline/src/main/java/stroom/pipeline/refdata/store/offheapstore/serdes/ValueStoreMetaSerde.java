package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * < typeId >< referenceCount >
 * < 1 byte >< 3 bytes >
 * <p>
 * referenceCount stored as a 3 byte unsigned integer so a max
 * of ~16 million.
 */
public class ValueStoreMetaSerde implements Serde<ValueStoreMeta> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreMetaSerde.class);

    private static final UnsignedBytes REF_COUNT_UNSIGNED_BYTES = UnsignedBytesInstances.THREE;
    private static final byte[] REF_COUNT_ZERO = REF_COUNT_UNSIGNED_BYTES.toBytes(0);
    private static final byte[] REF_COUNT_ONE = REF_COUNT_UNSIGNED_BYTES.toBytes(1);

    private static final int TYPE_ID_OFFSET = 0;
    private static final int TYPE_ID_BYTES = 1;
    private static final int REFERENCE_COUNT_OFFSET = TYPE_ID_OFFSET + TYPE_ID_BYTES;

    private static final int REFERENCE_COUNT_BYTES = REF_COUNT_UNSIGNED_BYTES.length();
    private static final int BUFFER_CAPACITY = TYPE_ID_BYTES + REFERENCE_COUNT_BYTES;

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }

    @Override
    public ValueStoreMeta deserialize(final ByteBuffer byteBuffer) {

        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(
                byteBuffer.get(),
                (int) REF_COUNT_UNSIGNED_BYTES.get(byteBuffer));
        byteBuffer.flip();

        return valueStoreMeta;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final ValueStoreMeta valueStoreMeta) {

        byteBuffer.put((byte) valueStoreMeta.getTypeId());
        REF_COUNT_UNSIGNED_BYTES.put(byteBuffer, valueStoreMeta.getReferenceCount());
        byteBuffer.flip();
    }

    /**
     * Leaves byteBuffer unchanged
     */
    public byte extractTypeId(final ByteBuffer byteBuffer) {
        return byteBuffer.get(TYPE_ID_OFFSET);
    }

    /**
     * Leaves byteBuffer unchanged
     */
    public int extractReferenceCount(final ByteBuffer byteBuffer) {
        return (int) REF_COUNT_UNSIGNED_BYTES.get(byteBuffer, REFERENCE_COUNT_OFFSET);
    }

    /**
     * @return True if the reference count is one or zero.
     */
    public boolean isLastReference(final ByteBuffer byteBuffer) {
        // Ever so slightly cheaper than extracting the count and checking the long value
        // TODO could maybe use ByteBufferUtils.equals
        return stroom.bytebuffer.hbase.ByteBufferUtils.compareTo(
                REF_COUNT_ONE, 0, REFERENCE_COUNT_BYTES,
                byteBuffer, REFERENCE_COUNT_OFFSET, REFERENCE_COUNT_BYTES) == 0L
               ||
               stroom.bytebuffer.hbase.ByteBufferUtils.compareTo(
                       REF_COUNT_ZERO, 0, REFERENCE_COUNT_BYTES,
                       byteBuffer, REFERENCE_COUNT_OFFSET, REFERENCE_COUNT_BYTES) == 0L;
    }

    public void cloneAndDecrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        ByteBufferUtils.copy(sourceBuffer, destBuffer);
        try {
            REF_COUNT_UNSIGNED_BYTES.decrement(destBuffer, REFERENCE_COUNT_OFFSET);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error decrementing reference count. Current value {}. {}",
                    REF_COUNT_UNSIGNED_BYTES.get(sourceBuffer, sourceBuffer.position()),
                    e.getMessage()), e);
        }
    }

    public void cloneAndIncrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        ByteBufferUtils.copy(sourceBuffer, destBuffer);
        try {
            REF_COUNT_UNSIGNED_BYTES.increment(destBuffer, REFERENCE_COUNT_OFFSET);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error incrementing reference count. Current value {}. {}",
                    ModelStringUtil.formatCsv(REF_COUNT_UNSIGNED_BYTES.get(sourceBuffer, REFERENCE_COUNT_OFFSET)),
                    e.getMessage()), e);
        }
    }

    public static long getMaxReferenceCount() {
        return REF_COUNT_UNSIGNED_BYTES.maxValue();
    }
}
