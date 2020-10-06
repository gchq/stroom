package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.pipeline.refdata.store.offheapstore.UnsignedBytes;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.pipeline.refdata.store.offheapstore.lmdb.serde.Serde;
import stroom.pipeline.refdata.util.ByteBufferUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * < typeId >< referenceCount >
 * < 1 byte >< 3 bytes >
 *
 *  referenceCount stored as a 3 byte unsigned integer so a max
 *  of ~1.6 million.
 */
public class ValueStoreMetaSerde implements Serde<ValueStoreMeta> {

    Logger LOGGER = LoggerFactory.getLogger(ValueStoreMetaSerde.class);

    private static final int TYPE_ID_OFFSET = 0;
    private static final int TYPE_ID_BYTES = 1;
    private static final int REFERENCE_COUNT_OFFSET = TYPE_ID_OFFSET + TYPE_ID_BYTES;

    private static final int REFERENCE_COUNT_BYTES = 3;
    private static final int BUFFER_CAPACITY = TYPE_ID_BYTES + REFERENCE_COUNT_BYTES;

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }

    @Override
    public ValueStoreMeta deserialize(ByteBuffer byteBuffer) {

        ValueStoreMeta valueStoreMeta = new ValueStoreMeta(
                byteBuffer.get(),
                (int) UnsignedBytes.get(byteBuffer, REFERENCE_COUNT_BYTES));
        byteBuffer.flip();

        return valueStoreMeta;
    }

    @Override
    public void serialize(ByteBuffer byteBuffer, ValueStoreMeta valueStoreMeta) {

        byteBuffer.put((byte) valueStoreMeta.getTypeId());
        UnsignedBytes.put(byteBuffer, REFERENCE_COUNT_BYTES, valueStoreMeta.getReferenceCount());
        byteBuffer.flip();
    }

    /**
     * Leaves byteBuffer unchanged
     */
    public int extractTypeId(final ByteBuffer byteBuffer) {
        return byteBuffer.get(TYPE_ID_OFFSET);
    }

    /**
     * Leaves byteBuffer unchanged
     */
    public int extractReferenceCount(final ByteBuffer byteBuffer) {
        return (int) UnsignedBytes.get(byteBuffer, REFERENCE_COUNT_OFFSET, REFERENCE_COUNT_BYTES);
    }

    public void cloneAndDecrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        ByteBufferUtils.copy(sourceBuffer, destBuffer);
        UnsignedBytes.decrement(destBuffer, REFERENCE_COUNT_OFFSET, REFERENCE_COUNT_BYTES);
    }

    public void cloneAndIncrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        ByteBufferUtils.copy(sourceBuffer, destBuffer);
        UnsignedBytes.increment(destBuffer, REFERENCE_COUNT_OFFSET, REFERENCE_COUNT_BYTES);
    }

}
