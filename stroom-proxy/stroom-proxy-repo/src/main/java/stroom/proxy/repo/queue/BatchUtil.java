package stroom.proxy.repo.queue;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class BatchUtil {

    public static <T> void transfer(final Supplier<Batch<T>> batchSupplier,
                                    final Consumer<Batch<T>> consumer) {
        boolean full = true;
        // Keep processing full batches or else we will schedule a new fill.
        while (full) {
            final Batch<T> batch = batchSupplier.get();
            full = batch.full();
            if (!batch.isEmpty()) {
                consumer.accept(batch);
            }
        }
    }

    public static <T> void transferEach(final Supplier<Batch<T>> supplier,
                                        final Consumer<T> consumer) {
        transfer(supplier, batch -> {
            for (final T t : batch.list()) {
                consumer.accept(t);
            }
        });
    }
}
