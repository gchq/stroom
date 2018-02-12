package stroom.util.concurrent;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A concurrency wrapper around objects that are lazily fetched.
 * In addition to lazy fetching, this allows any owner to indicate that the shared instance must be rebuilt,
 * it will also automatically call the supplier when the age of the shared instance passes a threshold.
 *
 * @param <T> The type of object being protected.
 */
public class AgeingLockableCache<T> implements Supplier<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Supplier<T> instanceSupplier;
    private final Supplier<Long> timeSupplier;
    private final Long maximumAge;

    private volatile boolean isBuilding = false;
    private volatile boolean rebuildRequired = true;
    private volatile T sharedInstance;
    private volatile long lastBuildTime = 0L;

    private AgeingLockableCache(final Supplier<T> instanceSupplier,
                                final Supplier<Long> timeSupplier,
                                final Long maximumAge) {
        this.instanceSupplier = Optional.ofNullable(instanceSupplier)
                .orElseThrow(() -> new IllegalArgumentException("Instance Supplier cannot be null for ageing lockable"));
        this.timeSupplier = Optional.ofNullable(timeSupplier)
                .orElseThrow(() -> new IllegalArgumentException("Time Supplier cannot be null for ageing lockable"));
        this.maximumAge = Optional.ofNullable( maximumAge)
                .orElseThrow(() -> new IllegalArgumentException("Maximum Age cannot be null for ageing lockable"));
    }

    /**
     * Check the time of the last build against our time supplier,
     * @return True if the last build is recent enough, False if it too old and need to be re-run
     */
    private boolean lastRebuildIsTooOld() {
        return (lastBuildTime < (timeSupplier.get() - maximumAge));
    }

    /**
     * This determines if a rebuild is required, based on timing and the various flags.
     * This function should not actually write any values, because it is not thread safe
     *
     * @return If a rebuild is required for any reason
     */
    private boolean isRebuildRequired() {
        return (lastRebuildIsTooOld() || rebuildRequired || isBuilding);
    }

    /**
     * Retrieve the lockable cached value. It will call down to the underlying supplier if
     * it has been told to, or the value is too old.
     *
     * @return The cached value.
     */
    @Override
    public T get() {
        if (isRebuildRequired()) {
            // Try and get the map under lock.
            lock.lock();
            isBuilding = true;

            // If the time is still a factor, we need to set 'rebuildRequired' on so that the while loop actually executes
            if (lastRebuildIsTooOld()) {
                rebuildRequired = true;
            }

            try {

                // If calls are made to 'rebuildTree' while a build is happening, it will force it to go round again
                while (rebuildRequired) {
                    rebuildRequired = false;

                    // Record the last time we built the full tree.
                    sharedInstance = instanceSupplier.get();

                    lastBuildTime = timeSupplier.get();
                }
            } finally {
                isBuilding = false;
                lock.unlock();
            }
        }

        return sharedInstance;
    }

    public void forceRebuild() {
        this.rebuildRequired = true;
    }

    public static <T> Builder<T> protect() {
        return new Builder<>();
    }

    /**
     * A Builder class for an Ageing Lockable object.
     * @param <T>
     */
    public static class Builder<T> {
        private Supplier<T> instanceSupplier;
        private Supplier<Long> timeSupplier;
        private Long maximumAge;

        private Builder() {
            timeSupplier = System::currentTimeMillis;
        }

        public Builder<T> fetch(final Supplier<T> instanceSupplier) {
            this.instanceSupplier = instanceSupplier;
            return this;
        }

        public Builder<T> timeSupplier(final Supplier<Long> timeSupplier) {
            this.timeSupplier = timeSupplier;
            return this;
        }

        public Builder<T> maximumAge(final Long maximumAge) {
            this.maximumAge = maximumAge;
            return this;
        }

        public AgeingLockableCache<T> build() {
            return new AgeingLockableCache<>(this.instanceSupplier, this.timeSupplier, this.maximumAge);
        }
    }
}
