package stroom.proxy.app.handler;

import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ForwardDestination implements Managed, Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardDestination.class);

    private final SequentialDirQueue forwardQueue;
    private final SequentialDirQueue retryQueue;
    private CompletableFuture<Void> forwardingFuture;
    private CompletableFuture<Void> retryingFuture;
    private volatile boolean running;

    public ForwardDestination(final RepoDirProvider repoDirProvider,
                              final QueueMonitors queueMonitors,
                              final FileStores fileStores,
                              final String destinationName) {
        final String dirName = destinationName.replaceAll("[^a-zA-Z0-9-_]", "_");
        forwardQueue = new SequentialDirQueue(repoDirProvider.get().resolve("forward_" + dirName),
                queueMonitors,
                fileStores,
                100,
                "forward - " + destinationName,
                true);
        retryQueue = new SequentialDirQueue(repoDirProvider.get().resolve("retry_" + dirName),
                queueMonitors,
                fileStores,
                101,
                "retry - " + destinationName,
                true);
    }

    @Override
    public void add(final Path sourceDir) throws IOException {
        forwardQueue.add(sourceDir);
    }

    private void forwardDir(final Path path) {




    }

    @Override
    public synchronized void start() throws Exception {
        if (!running) {
            running = true;

            // TODO : We could introduce more threads here.

            forwardingFuture = CompletableFuture.runAsync(() -> {
                while (running) {
                    final SequentialDir sequentialDir = forwardQueue.next();
                    forwardDir(sequentialDir.getDir());
                    // Delete empty dirs.
                    sequentialDir.deleteEmptyParentDirs();
                }
            });
            retryQueue = CompletableFuture.runAsync(() -> {
                while (running) {
                    final SequentialDir sequentialDir = retryQueue.next();
                    retryDir(sequentialDir.getDir());
                    // Delete empty dirs.
                    sequentialDir.deleteEmptyParentDirs();
                }
            });
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            running = false;
            forwardingFuture.join();
            retryingFuture.join();
        }
    }
}
