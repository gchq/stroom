package stroom.util.concurrent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A value that is lazily initialised using a provided {@link Supplier}.
 *
 * @param <T> The type of the lazy value.
 */
public class LazyValue<T> {

    private final Supplier<T> valueSupplier;
    private transient volatile boolean isInitialised = false;
    // Volatile piggybacking ensures that a thread sees the right value
    // as long as we read value AFTER reading isInitialised and
    // write value BEFORE writing isInitialised.
    // Hence, no volatile on this one.
    private transient T value;

    private LazyValue(final Supplier<T> valueSupplier) {
        this.valueSupplier = Objects.requireNonNull(valueSupplier);
    }

    public static <T> LazyValue<T> initialisedBy(final Supplier<T> valueSupplier) {
        return new LazyValue<>(valueSupplier);
    }

    /**
     * @return The value, initialising it if required. No locks are used so
     * the valueSupplier may be called multiple times. This method should only
     * be used if valueSupplier is idempotent, side effect free and not too
     * costly to run.
     */
    public T getValueWithoutLocks() {
        if (!isInitialised) {
            // May be called >1 times by different threads
            final T newVal = valueSupplier.get();
            // Write order is important here due to volatile piggybacking
            // Must write to isInitialised last, so the change to value happensBefore it
            value = newVal;
            isInitialised = true;
            return newVal;
        }
        return value;
    }

    /**
     * @return The value, initialising it if required. If initialisation
     * is required synchronisation will be used to ensure the supplier is only
     * called once.
     * Use this method if supplier is not idempotent, has side effects or is
     * costly to run.
     */
    public T getValueWithLocks() {
        if (!isInitialised) {
            synchronized (this) {
                if (!isInitialised) {
                    final T newVal = valueSupplier.get();
                    // Write order is important here due to volatile piggybacking
                    // Must write to isInitialised last, so the change to value happensBefore it
                    value = newVal;
                    isInitialised = true;
                    return newVal;
                }
            }
        }
        return value;
    }

    public boolean isInitialised() {
        return isInitialised;
    }
}
