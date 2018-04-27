package stroom.refdata.saxevents;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO maybe rename to ValueProxy as we don't really want to supply a value
// as that means copying the data out of the bytebuffer. If we have supply and consume methods
// then we can handle different cases.
public class ValueProxy<V> {

    // held for the purpose of testing equality of a ValueProxy
    private final OffHeapInternPool pool;
    private final Key key;

    //used to keep a track of how many object hold a reference to this valueProxy
    private final AtomicInteger referenceCount = new AtomicInteger(0);


    ValueProxy(final OffHeapInternPool pool, final Key key) {
        Objects.requireNonNull(pool);
        this.pool = Objects.requireNonNull(pool);
        this.key = Objects.requireNonNull(key);
    }

    /**
     * Materialise the value that this is proxying. The useValue() method should be preferred
     * as this method will involve the added cost of copying the contents of the value.
     * @return An optional value, as the value may have been evicted from the pool. Callers
     * should expect to handle this possibility.
     */
    public Optional<V> supply() {
        return pool.get(this);
    }

    <T> Optional<T> useValue(
            final ValueProxy<V> valueProxy,
            final Function<ByteBuffer, T> valueMapper) {
        return pool.mapValue(valueProxy, valueMapper);
    }

    void useValue(
            final ValueProxy<V> valueProxy,
            final Consumer<ByteBuffer> valueConsumer) {
        pool.consumeValue(valueProxy, valueConsumer);
    }

    public void inrementReferenceCount(){
        referenceCount.incrementAndGet();
    }

    public void decrementReferenceCount(){
        referenceCount.decrementAndGet();
    }

    int getReferenceCount() {
        return referenceCount.get();
    }

    Key getKey() {
        return key;
    }

    // TODO add a 'void useValue(Consumer<ByteBuffer> consumer)' method so the caller can supply
    // a lamdba to run inside the txn and avoid a copy of the retrieved object.
    // Plus add a method in the pool to call the consumer's get method having got the value using
    // the key

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ValueProxy<?> that = (ValueProxy<?>) o;
        return Objects.equals(pool, that.pool) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pool, key);
    }

    @Override
    public String toString() {
        return "ValueProxy{" +
                "pool=" + pool +
                ", key=" + key +
                '}';
    }
}
