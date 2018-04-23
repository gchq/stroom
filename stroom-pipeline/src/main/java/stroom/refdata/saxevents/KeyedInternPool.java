package stroom.refdata.saxevents;

import org.apache.hadoop.hbase.util.Bytes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public interface KeyedInternPool<V extends KeyedInternPool.AbstractKeyedInternPoolValue> extends AutoCloseable {

    /**
     * Puts value into the pool if it doesn't already exist.
     * @param value The value to put
     * @return The key associated with the existing value or the key generated
     * for the new entry.
     */
    Key put(V value);

    /**
     * Gets a value from the pool
     * @param key The {@link Key} to find a value for
     * @param valueMapper The mapper to use to convert the key into an instance of V
     * @return An optional V
     */
    Optional<V> get(final Key key, final Function<ByteBuffer, V> valueMapper);

    /**
     * Clear all items from the pool
     */
    void clear();

    class Key {

        private final int hashCode;
        private final int uniqueId;

        Key(final int hashCode, final int uniqueId) {
            this.hashCode = hashCode;
            this.uniqueId = uniqueId;
        }

        int getHashCode() {
            return hashCode;
        }

        int getUniqueId() {
            return uniqueId;
        }

        byte[] toBytes() {
            // TODO should this be allocatedirect for lmdb?
            // TODO should we do this in an lmdb txn
            final ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES)
                    .putInt(hashCode)
                    .putLong(uniqueId);
//                    .put(Bytes.toBytes(hashCode))
//                    .put(Bytes.toBytes(uniqueId));
            return byteBuffer.array();
        }

        ByteBuffer toDirectByteBuffer() {
            // TODO should this be allocatedirect for lmdb?
            // TODO should we do this in an lmdb txn
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES + Long.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .putInt(hashCode)
                    .putLong(uniqueId);
//                    .put(Bytes.toBytes(hashCode))
//                    .put(Bytes.toBytes(uniqueId));
            return byteBuffer;
        }

        static Key fromBytes(final byte[] bytes) {
            // TODO should this be allocatedirect for lmdb?
            // TODO should we do this in an lmdb txn
            final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            return fromBytes(byteBuffer);
        }

        static Key fromBytes(final ByteBuffer byteBuffer) {
            // TODO should this be allocatedirect for lmdb?
            // TODO should we do this in an lmdb txn
            byteBuffer.order(ByteOrder.nativeOrder());
            int hashCode = byteBuffer.getInt();
            int uniqueId = byteBuffer.getInt();
            return new Key(hashCode, uniqueId);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Key key = (Key) o;
            return hashCode == key.hashCode &&
                    uniqueId == key.uniqueId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hashCode, uniqueId);
        }
    }

    abstract class AbstractKeyedInternPoolValue {

        public abstract boolean equals(Object obj);

        public abstract int hashCode();

        public abstract byte[] toBytes();

    }
}
