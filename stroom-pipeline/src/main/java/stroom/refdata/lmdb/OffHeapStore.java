package stroom.refdata.lmdb;

public interface OffHeapStore<T> {

    Key put(AbstractOffheapValue<T> value);

    T get(final Key key);

    class Key {

        private final String hashCode;
        private final long uniqueId;

        Key(final String hashCode, final long uniqueId) {
            this.hashCode = hashCode;
            this.uniqueId = uniqueId;
        }

        String getHashCode() {
            return hashCode;
        }

        long getUniqueId() {
            return uniqueId;
        }

        byte[] toBytes() {
            //TODO serialise parts to bytes
            return null;
        }
    }

    abstract class AbstractOffheapValue<T> {

        public abstract boolean equals(Object obj);

        public abstract int hashCode();
    }
}
