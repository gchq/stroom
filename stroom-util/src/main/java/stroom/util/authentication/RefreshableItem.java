package stroom.util.authentication;

import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RefreshableItem<T> implements Refreshable {

    private volatile PerishableItem<T> perishableItem;
    private final Supplier<PerishableItem<T>> itemSupplier;
    private final Duration refreshBuffer;
    private final String uuid;

    public RefreshableItem(final Supplier<PerishableItem<T>> itemSupplier,
                           final Duration refreshBuffer) {
        this.itemSupplier = Objects.requireNonNull(itemSupplier);
        this.perishableItem = Objects.requireNonNull(itemSupplier.get(),
                "itemSupplier supplied null item");
        this.refreshBuffer = refreshBuffer.isNegative()
                ? Duration.ZERO
                : refreshBuffer;
        this.uuid = UUID.randomUUID().toString();
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public T getItem() {
        return perishableItem.getItem();
    }

    @Override
    public boolean refresh(final Consumer<Refreshable> onRefreshAction) {
        perishableItem = itemSupplier.get();
        NullSafe.consume(this, onRefreshAction);
        return true;
    }

    @Override
    public Instant getExpireTime() {
        return perishableItem.getExpireTime();
    }

    @Override
    public long getExpireTimeEpochMs() {
        return perishableItem.getExpireTimeEpochMs();
    }

    @Override
    public long getRefreshBufferMs() {
        return refreshBuffer.toMillis();
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
        return Objects.equals(perishableItem, that.perishableItem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(perishableItem);
    }

    @Override
    public String toString() {
        return "RefreshableItem{" +
               "perishableItem=" + perishableItem +
               ", refreshBuffer=" + refreshBuffer +
               '}';
    }
}
