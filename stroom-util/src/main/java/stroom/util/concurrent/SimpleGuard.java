package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SimpleGuard implements Guard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleGuard.class);

    private final AtomicInteger inUseCount = new AtomicInteger(1);
    private final AtomicBoolean destroy = new AtomicBoolean();
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final Runnable destroyRunnable;

    public SimpleGuard(final Runnable destroyRunnable) {
        Objects.requireNonNull(destroyRunnable);
        this.destroyRunnable = destroyRunnable;
    }

    @Override
    public <R> R acquire(final Supplier<R> supplier) {
        inUseCount.incrementAndGet();
        if (destroy.get()) {
            // Don't allow new acquisitions if the intent is to destroy.
            release();
            throw new TryAgainException();
        }

        try {
            return supplier.get();
        } finally {
            release();
        }
    }

    private void release() {
        final int count = inUseCount.decrementAndGet();
        if (count <= 0) {
            // Make sure we only ever try to run the destroy hook once.
            if (destroyed.compareAndSet(false, true)) {
                destroyRunnable.run();
            }
        }
    }

    @Override
    public void destroy() {
        if (destroy.compareAndSet(false, true)) {
            // Perform final decrement. Close is either performed now if the guard is not acquired or will be
            // performed by the final thread that releases the acquisition.
            release();
        } else {
            LOGGER.debug("Guard already destroyed");
        }
    }
}