package stroom.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkQueue {

    private final int threadCount;
    private final LinkedBlockingQueue<Optional<Runnable>> queue;
    private final List<CompletableFuture<Void>> futures;

    public WorkQueue(final Executor executor,
                     final int threadCount,
                     final int capacity) {
        this.threadCount = threadCount;
        queue = new LinkedBlockingQueue<>(capacity);
        futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
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
            }, executor));
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

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
