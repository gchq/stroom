package stroom.proxy.app.handler;

import io.dropwizard.lifecycle.Managed;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ManagedQueue implements Managed {

    private final Supplier<Dir> supplier;
    private final Consumer<Path> consumer;
    private final int threads;

    private CompletableFuture<?>[] completableFutures;
    private boolean running;

    public ManagedQueue(final Supplier<Dir> supplier,
                        final Consumer<Path> consumer,
                        final int threads) {
        this.supplier = supplier;
        this.consumer = consumer;
        this.threads = threads;
    }

    @Override
    public synchronized void start() throws Exception {
        if (!running) {
            running = true;

            completableFutures = new CompletableFuture[threads];
            for (int i = 0; i < threads; i++) {
                completableFutures[i] = CompletableFuture.runAsync(() -> {
                    while (running) {
                        try (final Dir dir = supplier.get()) {
                            consumer.accept(dir.getPath());
                        }
                    }
                });
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            running = false;
            CompletableFuture.allOf(completableFutures).join();
            completableFutures = null;
        }
    }
}
