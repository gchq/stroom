package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Forwarder implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Forwarder.class);

    private final ForwardDirQueue incomingQueue;
    private final List<ForwardDestination> forwardDestinations = new ArrayList<>();
    private CompletableFuture<Void> completableFuture;
    private final NumberedDirProvider copiesDirProvider;
    private volatile boolean running;

    @Inject
    public Forwarder(final ForwardDirQueue incomingQueue,
                     final RepoDirProvider repoDirProvider,
                     final ProxyConfig proxyConfig,
                     final QueueMonitors queueMonitors,
                     final FileStores fileStores) {
        this.incomingQueue = incomingQueue;

        final List<ForwardConfig> forwardDestinations = proxyConfig.getForwardDestinations();
        if (forwardDestinations == null || forwardDestinations.isEmpty()) {
            throw new RuntimeException("No forward destinations are configured");
        }

        forwardDestinations.forEach(forwardConfig -> {
            if (forwardConfig.isEnabled()) {
                final ForwardDestination forwardDestination = new ForwardDestination(
                        repoDirProvider,
                        queueMonitors,
                        fileStores,
                        forwardConfig.getName());
                this.forwardDestinations.add(forwardDestination);
            }
        });

        // Make receiving zip dir.
        final Path copiesDir = repoDirProvider.get().resolve("copies");
        ensureDirExists(copiesDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(copiesDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(copiesDir));
        }
        copiesDirProvider = new NumberedDirProvider(copiesDir);
    }

    private void addDir(final Path dir) {
        try {
            final List<Path> paths = new ArrayList<>();

            // Create copies.
            for (int i = 0; i < forwardDestinations.size() - 1; i++) {
                final Path copy = copiesDirProvider.get();
                FileUtil.deepCopy(dir, copy);
                paths.add(copy);
            }

            // Move with the final one.
            paths.add(dir);

            // Add all items to outgoing queues.
            for (int i = 0; i < forwardDestinations.size(); i++) {
                final ForwardDestination forwardDestination = forwardDestinations.get(i);
                final Path path = paths.get(i);
                forwardDestination.add(path);
            }

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void start() throws Exception {
        if (!running && !forwardDestinations.isEmpty()) {
            running = true;

            // TODO : We could introduce more threads here but probably don't need to.

            completableFuture = CompletableFuture.runAsync(() -> {
                while (running) {
                    final SequentialDir sequentialDir = incomingQueue.next();
                    addDir(sequentialDir.getDir());
                    // Delete empty dirs.
                    sequentialDir.deleteEmptyParentDirs();
                }
            });

            for (final ForwardDestination forwardDestination : forwardDestinations) {
                forwardDestination.start();
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            running = false;
            completableFuture.join();

            for (final ForwardDestination forwardDestination : forwardDestinations) {
                forwardDestination.stop();
            }
        }
    }

    private void ensureDirExists(final Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create " + FileUtil.getCanonicalPath(dir), e);
            throw new UncheckedIOException(e);
        }
    }
}
