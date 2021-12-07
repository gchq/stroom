package stroom.util.concurrent;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class WorkQueue {

    private final int threadCount;
    private final ArrayBlockingQueue<Optional<Runnable>> queue;
    private final CompletableFuture<Void>[] futures;

    @SuppressWarnings("unchecked")
    public WorkQueue(final Executor executor,
                     final int threadCount,
                     final int capacity) {
        this.threadCount = threadCount;
        queue = new ArrayBlockingQueue<>(capacity);
        futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Optional<Runnable> optional = queue.take();
                    while (optional.isPresent()) {
                        optional.get().run();
                        optional = queue.take();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }, executor);
        }
    }

    public void exec(final Runnable runnable) {
        try {
            queue.put(Optional.of(runnable));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void join() {
        for (int i = 0; i < threadCount; i++) {
            try {
                queue.put(Optional.empty());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        CompletableFuture.allOf(futures).join();
    }
}
