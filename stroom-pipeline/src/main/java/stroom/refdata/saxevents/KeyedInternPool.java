package stroom.refdata.saxevents;

import java.util.Collections;
import java.util.Map;

// TODO rename to UniqueValuePool?
public interface KeyedInternPool<V extends KeyedInternPool.AbstractKeyedInternPoolValue> extends AutoCloseable {

//    /**
//     * Puts value into the pool if it doesn't already exist.
//     *
//     * @param value The value to put
//     * @return The key associated with the existing value or the key generated
//     * for the new entry.
//     */
//    @Deprecated
//    Key put(V value);

    /**
     * Puts value into the pool if it doesn't already exist. The value must be serializable
     * to bytes. Equality will be determined on the serialised form.
     *
     * @param value The value to put
     * @return A supplier of the value now interned in the pool. The supplier acts as a lightweight
     * proxy to the actual value.
     */
    ValueSupplier<V> intern(V value);

//    /**
//     * Gets a value from the pool
//     *
//     * @param key The {@link Key} to find a value for
//     * @return An optional V
//     */
//    Optional<V> get(final Key key);

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

    abstract class AbstractKeyedInternPoolValue {

        public abstract boolean equals(Object obj);

        public abstract int hashCode();

        public abstract byte[] toBytes();

    }
}
