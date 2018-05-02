package stroom.refdata.saxevents;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO rename to UniqueValuePool?
public interface OffHeapInternPool<V extends AbstractPoolValue> extends AutoCloseable {

    /**
     * Puts value into the pool if it doesn't already exist. The value must be serializable
     * to a byte[]. Equality will be determined on the byte[].
     *
     * @param value The value to put
     * @return A proxy object for the value now interned in the pool. The return proxy is a lightweight
     * pointer to the actual value. The proxy can be used to later obtain the actual value.
     */
    ValueProxy<V> intern(V value);

    /**
     * Gets the value form the pool associated with the passed valueProxy, an an empty {@link Optional}
     * if no value can be found. This method will involve making a copy of the data in the pool, so
     * the mapValue and consumeValue methods should be used in preference.
     */
    Optional<V> get(final ValueProxy<V> valueProxy);

    /**
     * This method will find the value associated with valueProxy in the pool and map it to T using the
     * supplied valueMapper. The mapping will be done inside a transaction to avoid unnecessary copy
     * operations, so should ideally be a short lived method.
     * @param valueProxy
     * @param valueMapper
     * @param <T>
     * @return The result of the mapping, or empty if the value could not be found in the pool
     */
    <T> Optional<T> mapValue(
            final ValueProxy<V> valueProxy,
            final Function<ByteBuffer, T> valueMapper);

    /**
     * This method will find the value associated with valueProxy in the pool and pass it to the valueConsumer.
     * The mapping will be done inside a transaction to avoid unnecessary copy operations, so
     * should ideally be a short lived method.
     * @param valueProxy
     * @param valueConsumer
     */
    void consumeValue(
            final ValueProxy<V> valueProxy,
            final Consumer<ByteBuffer> valueConsumer);


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

}
