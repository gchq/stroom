package stroom.proxy.app.handler;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DirQueueTransfer implements Runnable {

    private final Supplier<Dir> supplier;
    private final Consumer<Path> consumer;

    public DirQueueTransfer(final Supplier<Dir> supplier,
                            final Consumer<Path> consumer) {
        this.supplier = supplier;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (final Dir dir = supplier.get()) {
            consumer.accept(dir.getPath());
        }
    }
}
