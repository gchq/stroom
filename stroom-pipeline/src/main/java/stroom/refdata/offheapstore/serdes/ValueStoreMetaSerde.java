package stroom.refdata.offheapstore.serdes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.ValueStoreMeta;

import java.nio.ByteBuffer;

public class ValueStoreMetaSerde implements Serde<ValueStoreMeta> {

    Logger LOGGER = LoggerFactory.getLogger(RefDataValueSubSerde.class);

    public static final int TYPE_ID_OFFSET = 0;
    public static final int TYPE_ID_BYTES = 1;
    public static final int REFERENCE_COUNT_OFFSET = TYPE_ID_OFFSET + TYPE_ID_BYTES;
    public static final int REFERENCE_COUNT_BYTES = Integer.BYTES;
    private static final int BUFFER_CAPACITY = TYPE_ID_BYTES + REFERENCE_COUNT_BYTES;

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }

    @Override
    public ValueStoreMeta deserialize(ByteBuffer byteBuffer) {

        ValueStoreMeta valueStoreMeta = new ValueStoreMeta(byteBuffer.get(), byteBuffer.getInt());
        byteBuffer.flip();

        return valueStoreMeta;
    }

    @Override
    public void serialize(ByteBuffer byteBuffer, ValueStoreMeta valueStoreMeta) {

        byteBuffer.put((byte) valueStoreMeta.getTypeId());
        byteBuffer.putInt(valueStoreMeta.getReferenceCount());
        byteBuffer.flip();
    }

    public int extractTypeId(final ByteBuffer byteBuffer) {
        return byteBuffer.get(TYPE_ID_OFFSET);
    }

    public int extractReferenceCount(final ByteBuffer byteBuffer) {
        return byteBuffer.getInt(REFERENCE_COUNT_OFFSET);
    }

    public int cloneAndDecrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        return cloneAndUpdateRefCount(sourceBuffer, destBuffer, -1);
    }

    public int cloneAndIncrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        return cloneAndUpdateRefCount(sourceBuffer, destBuffer, 1);
    }

    public int cloneAndUpdateRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer, int referenceCountDelta) {

        int currRefCount = extractReferenceCount(sourceBuffer);
        int newRefCount = currRefCount + referenceCountDelta;
        destBuffer.put(sourceBuffer.get(TYPE_ID_OFFSET));
        destBuffer.putInt(newRefCount);
        destBuffer.flip();

        return newRefCount;
    }


//    public int updateReferenceCount(final ByteBuffer byteBuffer, int referenceCountDelta) {
//        int currRefCount = extractReferenceCount(byteBuffer);
//        int newRefCount = currRefCount + referenceCountDelta;
//
//        byteBuffer.putInt(REFERENCE_COUNT_OFFSET, newRefCount);
//        LOGGER.trace("Changing ref count from {} to {}", currRefCount, newRefCount);
//        return newRefCount;
//    }
}
