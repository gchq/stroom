package stroom.util.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An item of type T that has a finite life as defined by its expiry time.
 *
 * @param <T>
 */
public class PerishableItem<T> implements HasExpiry {

    private final Instant expiryTime;
    private final T item;

    public PerishableItem(final Instant expiryTime, final T item) {
        this.expiryTime = Objects.requireNonNull(expiryTime);
        this.item = Objects.requireNonNull(item);
    }

    public PerishableItem(final Duration remaining, final T item) {
        this.expiryTime = Instant.now().plus(Objects.requireNonNull(remaining));
        this.item = Objects.requireNonNull(item);
    }

    @Override
    public Instant getExpireTime() {
        return expiryTime;
    }

    public T getItem() {
        return item;
    }

    @Override
    public String toString() {
        return "Perishable{" +
               "expiryTime=" + expiryTime +
               ", item=" + item +
               '}';
    }
}
