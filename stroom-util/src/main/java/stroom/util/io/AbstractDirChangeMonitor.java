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

package stroom.util.io;

import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.lifecycle.Managed;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AbstractDirChangeMonitor implements HasHealthCheck, Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDirChangeMonitor.class);
    private static final long DELAY_BEFORE_FILE_READ_MS = 2_000;

    private final ExecutorService executorService;
    private WatchService watchService = null;
    private Future<?> watcherFuture = null;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final boolean isValidDir;
    private final AtomicBoolean isBatchScheduled = new AtomicBoolean(false);
    private final List<String> errors = new ArrayList<>();
    private final BlockingQueue<SimpleWatchEvent> queue = new LinkedBlockingQueue<>();

    protected final Predicate<Path> fileIncludeFilter;
    protected final Set<EventType> includedEventTypes;
    protected final Path dirToWatch;

    public AbstractDirChangeMonitor(final Path dirToWatch) {
        this(dirToWatch, null, EnumSet.allOf(EventType.class));
    }

    public AbstractDirChangeMonitor(final Path dirToWatch,
                                    final Predicate<Path> fileIncludeFilter,
                                    final Set<EventType> includedEventTypes) {
        LOGGER.debug("dirToWatch: {}, includedEventTypes: {}", dirToWatch, includedEventTypes);
        if (dirToWatch != null) {
            this.dirToWatch = dirToWatch.toAbsolutePath();
            this.fileIncludeFilter = fileIncludeFilter;
            this.includedEventTypes = includedEventTypes;
            if (!Files.isDirectory(dirToWatch)) {
                throw new RuntimeException(LogUtil.message("{} is not a directory", this.dirToWatch));
            }
            this.isValidDir = true;
            this.executorService = Executors.newSingleThreadExecutor();
        } else {
            // This will prevent it starting
            this.isValidDir = false;
            this.dirToWatch = null;
            this.executorService = null;
            this.fileIncludeFilter = null;
            this.includedEventTypes = null;
        }
    }

    private Set<SimpleWatchEvent> createEmptySet() {
        // Should be only one thread touching it at once anyway, either the watcher thread writing
        // or the scheduled thread reading, but not at the same time.
        // LinkedHashSet to preserve event order
        return Collections.synchronizedSet(new LinkedHashSet<>());
    }

    public Path getDirToWatch() {
        return dirToWatch;
    }

    /**
     * Starts the object. Called <i>before</i> the application becomes available.
     */
    public void start() {
        if (isValidDir) {
            try {
                startWatcher();
                onInitialisation();
            } catch (final Exception e) {
                // Swallow and log as we don't want to stop the app from starting just for this
                errors.add(e.getMessage());
                LOGGER.error(
                        "Unable to start dir monitor for dir {} due to [{}]. Changes will not be monitored.",
                        dirToWatch.toAbsolutePath(),
                        e.getMessage(),
                        e);
            }
        } else {
            LOGGER.error(() -> LogUtil.message("Unable to start watcher as {} is not a valid file",
                    NullSafe.toString(dirToWatch, path -> path.toAbsolutePath().normalize())));
        }
    }

    private Kind<?>[] getKindsToWatch() {
        return includedEventTypes.stream()
                .map(EventType::getKind)
                .toArray(Kind[]::new);
    }

    private void startWatcher() throws IOException {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating watch new service, {}", e.getMessage()), e);
        }

        dirToWatch.register(watchService, getKindsToWatch());

        // run the watcher in its own thread else it will block app startup
        watcherFuture = CompletableFuture.runAsync(() -> {
            WatchKey watchKey = null;

            LOGGER.info("Starting file modification watcher for {}", dirToWatch.toAbsolutePath().normalize());
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

                final List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
                LOGGER.debug(() -> LogUtil.message("Received {} events", watchEvents.size()));

                for (final WatchEvent<?> event : watchEvents) {
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

                    if (event != null) {
                        if (event.kind().equals(StandardWatchEventKinds.OVERFLOW)) {
                            LOGGER.warn("{} event detected breaking out.", event.kind().name());
                            if (includedEventTypes.contains(EventType.OVERFLOW)) {
                                onOverflow();
                            }
                            break;
                        }
                        if (event.kind() != null && Path.class.isAssignableFrom(event.kind().type())) {
                            handleWatchEvent((WatchEvent<Path>) event);
                        } else {
                            LOGGER.debug("Not an event we care about");
                        }
                    }
                }
                final boolean isValid = watchKey.reset();
                if (!isValid) {
                    LOGGER.warn("Watch key is no longer valid, the watch service may have been stopped");
                    break;
                }
            }
        }, executorService);
    }

    private void handleWatchEvent(final WatchEvent<Path> pathEvent) {
        final WatchEvent.Kind<Path> kind = pathEvent.kind();
        final Path affectedFile = dirToWatch.resolve(pathEvent.context())
                .normalize()
                .toAbsolutePath();
        LOGGER.debug(() -> LogUtil.message(
                "handleWatchEvent - kind: {}, affectedFile: {}", kind.name(), affectedFile));

        try {
            final EventType eventType = EventType.fromKind(kind);
            if (eventType != null
                && includedEventTypes.contains(eventType)) {

                // Add the event to our batch.
                queue.add(new SimpleWatchEvent(eventType, affectedFile));
                scheduleBatchIfRequired();
            } else {
                LOGGER.debug("Ignoring eventType: {} on affectedFile: {}", eventType, affectedFile);
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling watch event, kind: {}, affectedFile: {}", kind.name(), affectedFile, e);
            // Swallow error so future changes can be monitored.
        }
    }

    private synchronized void scheduleBatchIfRequired() {

        // When a file is changed the filesystem can trigger two changes, one to change the file content
        // and another to change the file access time. To prevent a duplicate read we delay the read
        // a bit so we can have many changes during that delay period but with only one read of the file.
        if (isBatchScheduled.compareAndSet(false, true)) {
            LOGGER.info("Scheduling call to change listener for file {} in {}ms",
                    dirToWatch.toAbsolutePath().normalize(),
                    DELAY_BEFORE_FILE_READ_MS);

            CompletableFuture.delayedExecutor(DELAY_BEFORE_FILE_READ_MS, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        try {
                            synchronized (this) {
                                // Atomically swap out the set with an empty one
                                final List<SimpleWatchEvent> allEvents = new ArrayList<>();
                                queue.drainTo(allEvents);

                                LOGGER.debug(() -> LogUtil.message("Draining batch of {} events", allEvents.size()));
                                final Map<Path, List<SimpleWatchEvent>> groupedByPath = allEvents.stream()
                                        .collect(Collectors.groupingBy(
                                                SimpleWatchEvent::path,
                                                Collectors.toList()));

                                for (final Entry<Path, List<SimpleWatchEvent>> entry : groupedByPath.entrySet()) {
                                    final Path path = entry.getKey();
                                    List<SimpleWatchEvent> eventsForPath = entry.getValue();
                                    LOGGER.debug("path: {}, simpleWatchEvents: {}", path, eventsForPath);

                                    if (NullSafe.hasItems(eventsForPath)) {
                                        eventsForPath = deDupEvents(eventsForPath);

                                        // Now fire all the de-duped events
                                        for (final SimpleWatchEvent event : eventsForPath) {
                                            final Path affectedFile = event.path;
                                            switch (event.eventType) {
                                                case MODIFY -> onEntryModify(affectedFile);
                                                case CREATE -> onEntryCreate(affectedFile);
                                                case DELETE -> onEntryDelete(affectedFile);
                                            }
                                        }
                                    }
                                }

                            }
                        } finally {
                            isBatchScheduled.set(false);
                        }
                    });
        }
    }

    private List<SimpleWatchEvent> deDupEvents(final List<SimpleWatchEvent> events) {
        if (NullSafe.hasItems(events)) {
            final Path firstPath = events.getFirst().path;
            EventType lastEventType = null;
            final List<SimpleWatchEvent> filteredEvents = new ArrayList<>(events.size());
            if (events.getLast().eventType == EventType.DELETE) {
                // DELETE is last so can ignore all other events
                filteredEvents.add(events.getLast());
            } else {
                for (final SimpleWatchEvent event : events) {
                    if (!Objects.equals(firstPath, event.path)) {
                        throw new RuntimeException("All paths should be the same in this list.");
                    }
                    // Drop latest one in MODIFY,MODIFY or CREATE,MODIFY
                    if (event.eventType == lastEventType
                        || (event.eventType == EventType.MODIFY && lastEventType == EventType.CREATE)) {
                        LOGGER.debug("Dropping event {}", event);
                    } else {
                        filteredEvents.add(event);
                        lastEventType = event.eventType;
                    }
                }
            }
            return filteredEvents;
        } else {
            return events;
        }
    }

    /**
     * Stops the object. Called <i>after</i> the application is no longer accepting requests.
     *
     * @throws Exception if something goes wrong.
     */
    public void stop() throws Exception {
        if (isValidDir) {
            LOGGER.info("Stopping dir watcher for {}",
                    dirToWatch.toAbsolutePath().normalize());

            if (watchService != null) {
                watchService.close();
            }
            if (executorService != null) {
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
        if (dirToWatch == null) {
            resultBuilder.healthy()
                    .withMessage("No dir provided to monitor");
        } else if (isRunning.get()) {
            resultBuilder.healthy();
        } else {
            resultBuilder
                    .unhealthy()
                    .withDetail("errors", errors);
        }

        return resultBuilder
                .withDetail("monitoredDir", dirToWatch != null
                        ? dirToWatch.toAbsolutePath().normalize().toString()
                        : null)
                .withDetail("isRunning", isRunning)
                .withDetail("isValidDir", isValidDir)
                .build();
    }

    /**
     * Called once the directory monitor has been started
     */
    protected abstract void onInitialisation();

    /**
     * Called when a file/directory in the monitored directory is modified.
     */
    protected abstract void onEntryModify(final Path path);

    /**
     * Called when a file/directory in the monitored directory is created.
     */
    protected abstract void onEntryCreate(final Path path);

    /**
     * Called when a file/directory in the monitored directory is deleted.
     */
    protected abstract void onEntryDelete(final Path path);

    /**
     * Called when the consumption of events is not keeping up with the production.
     * If this is called, other events may have been dropped, so you may need
     * to re-scan the watched director.
     */
    protected abstract void onOverflow();


    // --------------------------------------------------------------------------------


    private record SimpleWatchEvent(EventType eventType,
                                    Path path) {

    }


    // --------------------------------------------------------------------------------


    public enum EventType {
        MODIFY(StandardWatchEventKinds.ENTRY_MODIFY),
        CREATE(StandardWatchEventKinds.ENTRY_CREATE),
        DELETE(StandardWatchEventKinds.ENTRY_DELETE),
        OVERFLOW(StandardWatchEventKinds.OVERFLOW),
        ;

        private static final Map<String, EventType> KIND_NAME_TO_EVENT_TYPE_MAP = Arrays.stream(values())
                .collect(Collectors.toMap(EventType::getKindName, Function.identity()));

        private final Kind<?> kind;

        EventType(final Kind<?> kind) {
            this.kind = kind;
        }

        public Kind<?> getKind() {
            return kind;
        }

        public String getKindName() {
            return kind.name();
        }

        public static EventType fromKind(final Kind<?> kind) {
            return NullSafe.get(kind, kind2 -> KIND_NAME_TO_EVENT_TYPE_MAP.get(kind2.name()));
        }
    }
}
