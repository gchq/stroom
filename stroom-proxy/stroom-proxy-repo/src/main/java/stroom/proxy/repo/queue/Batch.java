package stroom.proxy.repo.queue;

import java.util.Collections;
import java.util.List;

public record Batch<T>(List<T> list, boolean full) {
    private static final Batch<?> EMPTY_BATCH = new Batch<>(Collections.emptyList(), false);

    @SuppressWarnings("unchecked")
    public static <T> Batch<T> emptyBatch() {
        return (Batch<T>) EMPTY_BATCH;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }
}
