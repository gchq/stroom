package stroom.refdata.saxevents;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class ValueSupplier<V> {

    // held for the purpose of testing equality of a ValueSupplier
    private final KeyedInternPool pool;
    private final Key key;
    private final Supplier<Optional<V>> supplier;

    ValueSupplier(final KeyedInternPool pool, final Key key, final Supplier<Optional<V>> supplier) {
        Objects.requireNonNull(pool);
        this.pool = Objects.requireNonNull(pool);
        this.key = Objects.requireNonNull(key);
        this.supplier = Objects.requireNonNull(supplier);
    }

    /**
     * @return An optional value, as the value may have been evicted from the pool. Callers
     * should expect to handle this possibility.
     */
    public Optional<V> supply() {
        return supplier.get();
    }

    // TODO add a 'void useValue(Consumer<ByteBuffer> consumer)' method so the caller can supply
    // a lamdba to run inside the txn and avoid a copy of the retrieved object.
    // Plus add a method in the pool to call the consumer's get method having got the value using
    // the key

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ValueSupplier<?> that = (ValueSupplier<?>) o;
        return Objects.equals(pool, that.pool) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pool, key);
    }

    @Override
    public String toString() {
        return "ValueSupplier{" +
                "pool=" + pool +
                ", key=" + key +
                '}';
    }
}
