package stroom.util.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RefreshableItem<T> implements Refreshable {

    private volatile T item;
    private final Supplier<T> itemSupplier;
    private final Duration maxAge;
    private volatile long expireTimeEpochMs;

    public RefreshableItem(final Supplier<T> itemSupplier,
                           final Duration maxAge) {
        this.itemSupplier = Objects.requireNonNull(itemSupplier);
        this.item = itemSupplier.get();
        this.maxAge = maxAge;
        this.expireTimeEpochMs = Instant.now().plus(maxAge).toEpochMilli();
    }

    public T getItem() {
        return item;
    }

    @Override
    public boolean isRefreshRequired() {
        final boolean isExpired = System.currentTimeMillis() >= expireTimeEpochMs;
        LOGGER.trace("isExpired: {}", isExpired);
        return isExpired;
    }

    @Override
    public boolean refresh() {
        item = itemSupplier.get();
        updateExpireTime();
        return true;
    }

    @Override
    public long getExpireTimeEpochMs() {
        return expireTimeEpochMs;
    }

    @Override
    public int compareTo(final Delayed other) {
        return Math.toIntExact(this.expireTimeEpochMs
                - ((stroom.util.authentication.RefreshableItem<?>) other).expireTimeEpochMs);
    }

    @Override
    public long getDelay(final TimeUnit unit) {
        long diff = expireTimeEpochMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    private void updateExpireTime() {
        expireTimeEpochMs = Instant.now().plus(maxAge).toEpochMilli();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RefreshableItem<?> that = (RefreshableItem<?>) o;
        return Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item);
    }
}
