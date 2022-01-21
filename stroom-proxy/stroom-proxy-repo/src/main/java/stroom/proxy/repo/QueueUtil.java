package stroom.proxy.repo;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class QueueUtil {

    private QueueUtil() {
        // Utility class.
    }

    public static <T> void consumeAll(final Supplier<Optional<T>> supplier,
                                      final Consumer<T> consumer) {
        T t = supplier.get().orElse(null);
        while (t != null) {
            consumer.accept(t);
            t = supplier.get().orElse(null);
        }
    }
}
