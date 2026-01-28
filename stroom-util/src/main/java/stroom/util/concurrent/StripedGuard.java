package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Objects;import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class StripedGuard implements Guard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StripedGuard.class);

    private static final int DEFAULT_STRIPES = 64;  // Diminishing returns above this point.
    private static final int MAX_STRIPES = 256;  // Reasonable limit

    private final int stripeMask;
    private final Stripe[] stripes;

    public StripedGuard(final Runnable destroyRunnable) {
        this(destroyRunnable, DEFAULT_STRIPES);
    }

    public StripedGuard(final Runnable destroyRunnable, final int stripeCount) {
        Objects.requireNonNull(destroyRunnable);

        // Validate: positive
        if (stripeCount <= 0) {
            throw new IllegalArgumentException(
                    "Stripe count must be positive, got: " + stripeCount);
        }

        // Validate: not too large
        if (stripeCount > MAX_STRIPES) {
            throw new IllegalArgumentException(
                    "Stripe count exceeds maximum. Got: " + stripeCount +
                    ", max: " + MAX_STRIPES);
        }

        // Validate: power of 2
        if ((stripeCount & (stripeCount - 1)) != 0) {
            throw new IllegalArgumentException(
                    "Stripe count must be power of 2, got: " + stripeCount);
        }

        this.stripeMask = stripeCount - 1;
        final AtomicInteger destroyCount = new AtomicInteger(stripeCount);
        this.stripes = new Stripe[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            stripes[i] = new Stripe(destroyCount, destroyRunnable);
        }
    }

    private int getStripeIdx() {
        final long tid = Thread.currentThread().threadId();
        // XOR-fold upper and lower 32 bits for better distribution
        return (int) ((tid ^ (tid >>> 32)) & stripeMask);
    }

    @Override
    public <R> R acquire(final Supplier<R> supplier) {
        return stripes[getStripeIdx()].acquire(supplier);
    }

    @Override
    public void destroy() {
        // Destroy all stripes.
        for (final Stripe stripe : stripes) {
            stripe.destroy();
        }
    }

    private static final class Stripe {

        private final AtomicInteger inUseCount = new AtomicInteger(1);
        private final AtomicInteger destroyCount;
        private final AtomicBoolean destroy = new AtomicBoolean();
        private final AtomicBoolean destroyed = new AtomicBoolean();
        private final Runnable destroyRunnable;

        public Stripe(final AtomicInteger destroyCount,
                      final Runnable destroyRunnable) {
            this.destroyCount = destroyCount;
            this.destroyRunnable = destroyRunnable;
        }

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
                    if (destroyCount.decrementAndGet() == 0) {
                        destroyRunnable.run();
                    }
                }
            }
        }

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
}