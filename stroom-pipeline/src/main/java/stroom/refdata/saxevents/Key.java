package stroom.refdata.saxevents;

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A surrogate key for a value that implements hashCode(). It is recomended that the
 * hashcode of the value is held on the instance rather than computing it on the fly
 * each time to save processing.
 */
class Key {

    private static final int SIZE_IN_BYTES = Integer.BYTES + Integer.BYTES;
    private static final int DEFAULT_UNIQUE_ID = 0;

    // The hashcode of the value that this key points to
    private final int valueHashCode;
    // An ID to provide uniqueness in the event of a hash-clash
    private final int uniqueId;

    Key(final int valueHashCode) {
        this(valueHashCode, DEFAULT_UNIQUE_ID);
    }

    Key(final int valueHashCode, final int uniqueId) {
        // Due to the way keys are sorted, negative unique ids are not supported
        Preconditions.checkArgument(uniqueId >= 0);
        this.valueHashCode = valueHashCode;
        this.uniqueId = uniqueId;
    }

    /**
     * @return A new Key instance with the next unique ID. Should be used with some form of
     * concurrency protection to avoid multiple keys with the same ID.
     */
    Key nextKey() {
        return new Key(valueHashCode, uniqueId + 1);
    }

    int getValueHashCode() {
        return valueHashCode;
    }

    int getUniqueId() {
        return uniqueId;
    }

//        byte[] toBytes() {
//            // TODO should this be allocatedirect for lmdb?
//            // TODO should we do this in an lmdb txn
//            final ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
//            putContent(byteBuffer);
//            return byteBuffer.array();
//        }

//    ByteBuffer toDirectByteBuffer() {
//        // TODO should this be allocatedirect for lmdb?
//        // TODO should we do this in an lmdb txn
////        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
//        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect();
////                    .order(ByteOrder.nativeOrder());
//        putContent(byteBuffer, valueHashCode, uniqueId);
//        return byteBuffer;
//    }

    public static ByteBuffer putContent(final ByteBuffer byteBuffer, final int valueHashCode, final int uniqueId) {
        return byteBuffer
                .putInt(valueHashCode)
                .putInt(uniqueId);
//                    .put(Bytes.toBytes(valueHashCode))
//                    .put(Bytes.toBytes(uniqueId));
    }

//        static Key fromBytes(final byte[] bytes) {
//            // TODO should this be allocatedirect for lmdb?
//            // TODO should we do this in an lmdb txn
//            final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
//            return fromBytes(byteBuffer);
//        }

    static Key fromBytes(final ByteBuffer byteBuffer) {
        // TODO should this be allocatedirect for lmdb?
        // TODO should we do this in an lmdb txn
//            byteBuffer.order(ByteOrder.nativeOrder());
        int hashCode = byteBuffer.getInt();
        int uniqueId = byteBuffer.getInt();
        byteBuffer.flip();
        return new Key(hashCode, uniqueId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Key key = (Key) o;
        return valueHashCode == key.valueHashCode &&
                uniqueId == key.uniqueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(valueHashCode, uniqueId);
    }


    @Override
    public String toString() {
        return "Key{" +
                "valueHashCode=" + valueHashCode +
                ", uniqueId=" + uniqueId +
                ", bytes=" + getBytesString() +
                '}';
    }

    private String getBytesString() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SIZE_IN_BYTES);
        putContent(byteBuffer, valueHashCode, uniqueId);
        return LmdbUtils.byteArrayToHex(byteBuffer.array());
    }
}
