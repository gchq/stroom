package stroom.refdata.saxevents;

import com.google.common.base.Preconditions;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface KeyedInternPool<V extends KeyedInternPool.AbstractKeyedInternPoolValue> extends AutoCloseable {

    /**
     * Puts value into the pool if it doesn't already exist.
     *
     * @param value The value to put
     * @return The key associated with the existing value or the key generated
     * for the new entry.
     */
    Key put(V value);

    /**
     * Gets a value from the pool
     *
     * @param key The {@link Key} to find a value for
     * @return An optional V
     */
    Optional<V> get(final Key key);

    /**
     * Clear all items from the pool
     */
    void clear();

    /**
     * @return The total number of entries in the pool
     */
    long size();

    default Map<String, String> getInfo() {
        return Collections.emptyMap();
    }

    default void close() {
        //do nothing
    }

    class Key {

        public static final int SIZE_IN_BYTES = Integer.BYTES + Integer.BYTES;
        private final int valueHashCode;
        private final int uniqueId;

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

        ByteBuffer toDirectByteBuffer() {
            // TODO should this be allocatedirect for lmdb?
            // TODO should we do this in an lmdb txn
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
//                    .order(ByteOrder.nativeOrder());
            putContent(byteBuffer, valueHashCode, uniqueId);
            return byteBuffer;
        }

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
            return byteArrayToHex(byteBuffer.array());
        }

        /**
         * Converts a byte array into a hex representation with a space between each
         * byte e.g 00 00 01 00 05 59 B3
         *
         * @param arr The byte array to convert
         * @return The byte array as a string of hex values separated by a spaces
         */
        public static String byteArrayToHex(final byte[] arr) {
            final StringBuilder sb = new StringBuilder();
            if (arr != null) {
                for (final byte b : arr) {
                    sb.append(byteToHex(b));
                    sb.append(" ");
                }
            }
            return sb.toString().replaceAll(" $", "");
        }

        public static String byteToHex(final byte b) {
            final byte[] oneByteArr = new byte[1];
            oneByteArr[0] = b;
            return DatatypeConverter.printHexBinary(oneByteArr);

        }
    }

    abstract class AbstractKeyedInternPoolValue {

        public abstract boolean equals(Object obj);

        public abstract int hashCode();

        public abstract byte[] toBytes();

    }
}
