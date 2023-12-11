package stroom.proxy.repo.queue;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TransferUtil {

    public static <T> void transfer(final Supplier<T> batchSupplier,
                                    final Consumer<T> consumer) {
        // Keep processing full batches or else we will schedule a new fill.
        while (!Thread.currentThread().isInterrupted()) {
            final T batch = batchSupplier.get();
            consumer.accept(batch);
        }
    }
}
