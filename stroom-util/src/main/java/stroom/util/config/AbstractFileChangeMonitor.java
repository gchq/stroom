/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.config;

import stroom.util.HasHealthCheck;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasPropertyPath;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractFileChangeMonitor implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractFileChangeMonitor.class);

    private final Path monitoredFile;
    private final Path dirToWatch;
    private final ExecutorService executorService;
    private WatchService watchService = null;
    private Future<?> watcherFuture = null;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final boolean isValidFile;
    private final AtomicBoolean isFileReadScheduled = new AtomicBoolean(false);
    private final List<String> errors = new ArrayList<>();

    private static final long DELAY_BEFORE_FILE_READ_MS = 2_000;

    public AbstractFileChangeMonitor(final Path monitoredFile) {
        this.monitoredFile = monitoredFile;

        // AbstractEndToEndTest runs with no physical file, so allow for that
        if (monitoredFile == null) {
            LOGGER.warn("No file supplied to monitor, this should only be the case in testing");
        }

        if (NullSafe.test(monitoredFile, Files::isRegularFile)) {
            isValidFile = true;

            dirToWatch = monitoredFile.toAbsolutePath().getParent();
            if (!Files.isDirectory(dirToWatch)) {
                throw new RuntimeException(LogUtil.message("{} is not a directory", dirToWatch));
            }
            executorService = Executors.newSingleThreadExecutor();
        } else {
            isValidFile = false;
            dirToWatch = null;
            executorService = null;
        }
    }

    protected abstract void onFileChange();

    /**
     * Starts the object. Called <i>before</i> the application becomes available.
     */
    public void start() {
        if (isValidFile) {
            try {
                startWatcher();
            } catch (final Exception e) {
                // Swallow and log as we don't want to stop the app from starting just for this
                errors.add(e.getMessage());
                LOGGER.error(
                        "Unable to start file monitor for file {} due to [{}]. Changes will not be monitored.",
                        monitoredFile.toAbsolutePath(),
                        e.getMessage(),
                        e);
            }
        } else {
            LOGGER.error(() -> LogUtil.message("Unable to start watcher as {} is not a valid file",
                    NullSafe.toString(monitoredFile, path -> path.toAbsolutePath().normalize())));
        }
    }

    private void startWatcher() throws IOException {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating watch new service, {}", e.getMessage()), e);
        }

        dirToWatch.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        // run the watcher in its own thread else it will block app startup
        // TODO @AT Change to use CompleteableFuture.runAsync()
        watcherFuture = executorService.submit(() -> {
            WatchKey watchKey = null;

            LOGGER.info("Starting file modification watcher for {}",
                    monitoredFile.toAbsolutePath().normalize());
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.debug("Thread interrupted, stopping watching directory {}",
                            dirToWatch.toAbsolutePath().normalize());
                    break;
                }

                try {
                    isRunning.compareAndSet(false, true);
                    // block until the watch service spots a change
                    watchKey = watchService.take();
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // continue to re-use the if block above
                    continue;
                }

                for (final WatchEvent<?> event : watchKey.pollEvents()) {
                    if (LOGGER.isDebugEnabled()) {
                        if (event == null) {
                            LOGGER.debug("Event is null");
                        } else {
                            final String name = event.kind() != null
                                    ? event.kind().name()
                                    : "kind==null";
                            final String type = event.kind() != null
                                    ? event.kind().type().getSimpleName()
                                    : "kind==null";
                            LOGGER.debug("Dir watch event {}, {}, {}", name, type, event.context());
                        }
                    }

                    if (event.kind().equals(StandardWatchEventKinds.OVERFLOW)) {
                        LOGGER.warn("{} event detected breaking out. Retry file change",
                                StandardWatchEventKinds.OVERFLOW.name());
                        break;
                    }
                    if (event.kind() != null && Path.class.isAssignableFrom(event.kind().type())) {
                        handleWatchEvent((WatchEvent<Path>) event);
                    } else {
                        LOGGER.debug("Not an event we care about");
                    }
                }
                final boolean isValid = watchKey.reset();
                if (!isValid) {
                    LOGGER.warn("Watch key is no longer valid, the watch service may have been stopped");
                    break;
                }
            }
        });
    }

    private void handleWatchEvent(final WatchEvent<Path> pathEvent) {
        final WatchEvent.Kind<Path> kind = pathEvent.kind();

        // Only trigger on modify events and when count is one to avoid repeated events
        if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            final Path modifiedFile = dirToWatch.resolve(pathEvent.context());

            LOGGER.debug("Modified file: {}", modifiedFile);

            // Don't need to do anything if we have already scheduled the listener
            if (!isFileReadScheduled.get()) {
                try {
                    // we don't care about changes to other files
                    if (Files.isRegularFile(modifiedFile) && Files.isSameFile(monitoredFile, modifiedFile)) {
                        LOGGER.info("Change detected to file {}", monitoredFile.toAbsolutePath().normalize());
                        scheduleUpdateIfRequired();
                    } else {
                        LOGGER.debug("Ignoring change to file {}", modifiedFile);
                    }
                } catch (final IOException e) {
                    // Swallow error so future changes can be monitored.
                    // We may get a NoSuchFileException from isSameFile() due to anisble creating temp files in the
                    // monitored dir which it then immediately deletes. We don't care about this.
                    LOGGER.debug("Error comparing paths {} and {}", monitoredFile, modifiedFile, e);
                }
            } else {
                LOGGER.debug("Listener already scheduled");
            }
        }
    }

    private synchronized void scheduleUpdateIfRequired() {

        // When a file is changed the filesystem can trigger two changes, one to change the file content
        // and another to change the file access time. To prevent a duplicate read we delay the read
        // a bit so we can have many changes during that delay period but with only one read of the file.
        if (isFileReadScheduled.compareAndSet(false, true)) {
            LOGGER.info("Scheduling call to change listener for file {} in {}ms",
                    monitoredFile.toAbsolutePath().normalize(),
                    DELAY_BEFORE_FILE_READ_MS);
            CompletableFuture.delayedExecutor(DELAY_BEFORE_FILE_READ_MS, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        try {
                            synchronized (this) {
                                onFileChange();
                            }
                        } finally {
                            isFileReadScheduled.set(false);
                        }
                    });
        }
    }

    protected void logUpdate(final Object destParent,
                             final Prop prop,
                             final Object oldPropValue,
                             final Object newPropValue) {
        final String fullPath = ((HasPropertyPath) destParent).getFullPathStr(prop.getName());
        if (LOGGER.isInfoEnabled()) {
            if (isStringTooLong(oldPropValue) || isStringTooLong(newPropValue)) {
                LOGGER.info("  Updating config value of {} (class: {}) from:\n{}\nto:\n{}",
                        fullPath,
                        destParent.getClass().getSimpleName(),
                        oldPropValue,
                        newPropValue);
            } else {
                LOGGER.info("  Updating config value of {} (class: {}) from: [{}] to: [{}]",
                        fullPath,
                        destParent.getClass().getSimpleName(),
                        oldPropValue,
                        newPropValue);
            }
        }
    }

    protected boolean isStringTooLong(final Object value) {
        return value != null && value.toString().length() > 20;
    }

    /**
     * Stops the object. Called <i>after</i> the application is no longer accepting requests.
     *
     * @throws Exception if something goes wrong.
     */
    public void stop() throws Exception {
        if (isValidFile) {
            LOGGER.info("Stopping file modification watcher for {}",
                    monitoredFile.toAbsolutePath().normalize());

            if (watchService != null) {
                watchService.close();
            }
            if (executorService != null) {
                watchService.close();
                if (watcherFuture != null
                    && !watcherFuture.isCancelled()
                    && !watcherFuture.isDone()) {
                    watcherFuture.cancel(true);
                }
                executorService.shutdown();
            }
        }
        isRunning.set(false);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        // isRunning will only be true if the file is also present and valid
        if (monitoredFile == null) {
            resultBuilder.healthy()
                    .withMessage("No file provided to monitor");
        } else if (isRunning.get()) {
            resultBuilder.healthy();
        } else {
            resultBuilder
                    .unhealthy()
                    .withDetail("errors", errors);
        }

        return resultBuilder
                .withDetail("monitoredFile", monitoredFile != null
                        ? monitoredFile.toAbsolutePath().normalize().toString()
                        : null)
                .withDetail("isRunning", isRunning)
                .withDetail("isValidFile", isValidFile)
                .build();
    }
}
