/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.event.model.Event;
import stroom.proxy.app.handler.Receiver;
import stroom.proxy.app.handler.RefusingReceiver;
import stroom.proxy.repo.store.FileStores;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.CommonSecurityContext;
import stroom.util.concurrent.UniqueId;
import stroom.util.io.ByteSize;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Batches single events into files the receiver can take ({@code designs/infrastructure/events.md}).
 * <p>
 * {@link #accept} applies the receipt policy and appends the event to its feed's open file, and
 * returns only when the bytes are as durable as the configured durability says. {@link #tryRoll},
 * a registry schedule, closes every open file that is due and then hands every closed file in the
 * directory to the receiver as a plain body, deleting it when the receiver returns. A file is open
 * while it carries {@link EventAppender#OPEN_SUFFIX} and closed once renamed, so what is on disk
 * says which is which and nothing is queued in memory: a file a previous run left behind is
 * renamed closed at construction and received on the first tick like any other.
 * </p>
 * <p>
 * A file the receiver refuses for a reason that will not change - a rejected feed, a bad body - is
 * moved to {@code failed/} after three attempts. A file that fails for any other reason, which is
 * the store or the queue being unavailable, is tried again every tick until it succeeds: the data
 * is safe where it is, and nobody else will retry it.
 * </p>
 */
@Singleton
public class EventStore {

    static final int MAX_REFUSALS = 3;
    static final String FAILED_DIR_NAME = "failed";
    static final String SOURCE = "event-store";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStore.class);

    private final Receiver receiver;
    private final CommonSecurityContext securityContext;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final Provider<EventStoreConfig> eventStoreConfigProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Path dir;
    private final Path failedDir;
    private final Map<FeedKey, EventAppender> open = new ConcurrentHashMap<>();
    private final Map<Path, Integer> refusals = new ConcurrentHashMap<>();
    private final EventSerialiser eventSerialiser = new EventSerialiser();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Inject
    public EventStore(final Receiver receiver,
                      final CommonSecurityContext securityContext,
                      final AttributeMapFilterFactory attributeMapFilterFactory,
                      final Provider<EventStoreConfig> eventStoreConfigProvider,
                      final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                      final Provider<ProxyConfig> proxyConfigProvider,
                      final DataDirProvider dataDirProvider,
                      final FileStores fileStores) {
        this.receiver = receiver;
        this.securityContext = securityContext;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.eventStoreConfigProvider = eventStoreConfigProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.proxyConfigProvider = proxyConfigProvider;
        this.dir = dataDirProvider.get().resolve("event");
        this.failedDir = dir.resolve(FAILED_DIR_NAME);
        ensureDirExists(dir);
        closeLeftovers();
        fileStores.add(0, "Event Store", dir);
    }

    /**
     * Apply the receipt policy and, if it accepts, append the event to its feed's open file. The
     * caller runs as the processing user, because the policy consults feed status.
     *
     * @return False if the policy dropped the event, in which case nothing is written.
     * @throws StroomStreamException If the policy rejected it, or this proxy does not receive.
     */
    public boolean accept(final AttributeMap attributeMap,
                          final UniqueId receiptId,
                          final String event) {
        if (receiver instanceof RefusingReceiver) {
            // A node that does not receive must say so here too, or every file it rolled would be
            // refused later with nobody left to tell.
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap, RefusingReceiver.MESSAGE);
        }
        if (!attributeMapFilterFactory.create().filter(attributeMap)) {
            LOGGER.debug("Dropped event {} by the receipt policy: {}", receiptId, attributeMap);
            return false;
        }
        // After the policy: it may have named the feed itself.
        final FeedKey feedKey = FeedKeyEncoder.from(attributeMap);
        final byte[] bytes;
        try {
            bytes = (eventSerialiser.serialise(receiptId, feedKey, attributeMap, event) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        // The write happens under the map's lock for this key, which is what keeps two writers
        // of one feed off the same file; a feed sharing a bin with another waits for its write.
        open.compute(feedKey, (key, current) -> {
            if (closed.get()) {
                throw new IllegalStateException("The event store has been closed");
            }
            EventAppender appender = current;
            if (appender != null && appender.shouldRoll(bytes.length)) {
                closeQuietly(appender);
                appender = null;
            }
            if (appender == null) {
                appender = newAppender(key);
            }
            try {
                appender.write(bytes);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            return appender;
        });
        return true;
    }

    /**
     * Close every open file that is due, then receive every closed file. Runs on the registry's
     * schedule, so one tick receives at most what the previous tick left plus what just rolled.
     */
    public void tryRoll() {
        open.keySet().forEach(feedKey -> open.computeIfPresent(feedKey, (key, appender) -> {
            if (appender.shouldRoll(0)) {
                closeQuietly(appender);
                return null;
            }
            return appender;
        }));
        receiveClosedFiles();
    }

    /**
     * Close the open files. Called once every writer and the schedule have stopped; an event
     * accepted after this is refused, and the files are the next start's.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            open.keySet().forEach(feedKey -> open.computeIfPresent(feedKey, (key, appender) -> {
                closeQuietly(appender);
                return null;
            }));
        }
    }

    /**
     * A close that fails is logged and the appender still let go: every write was forced already,
     * so the file is complete, and keeping a closed appender would refuse the feed for ever. A file
     * left under its open name is renamed at the next start.
     */
    private static void closeQuietly(final EventAppender appender) {
        try {
            appender.close();
        } catch (final IOException e) {
            LOGGER.error(() -> "Error closing " + appender + ": " + e.getMessage(), e);
        }
    }

    private EventAppender newAppender(final FeedKey feedKey) {
        Path file;
        do {
            file = EventStoreFile.createNew(dir, feedKey, Instant.now());
        } while (Files.exists(file) || Files.exists(EventAppender.openFileOf(file)));
        final EventStoreConfig config = eventStoreConfigProvider.get();
        // A closed file is one receipt, and the receiver bounds a body by maxRequestSize.
        final ByteSize maxRequestSize = receiveDataConfigProvider.get().getMaxRequestSize();
        final long maxByteCount = maxRequestSize == null
                ? config.getMaxByteCount()
                : Math.min(config.getMaxByteCount(), maxRequestSize.getBytes());
        return new EventAppender(
                file,
                Instant.now(),
                config.getMaxAge().getDuration(),
                config.getMaxEventCount(),
                maxByteCount,
                proxyConfigProvider.get().getDurability());
    }

    /**
     * A previous run's open files: nothing holds them, so they are closed.
     */
    private void closeLeftovers() {
        for (final Path openFile : list(EventAppender.OPEN_SUFFIX)) {
            final String name = openFile.getFileName().toString();
            final Path file = openFile.resolveSibling(
                    name.substring(0, name.length() - EventAppender.OPEN_SUFFIX.length()));
            try {
                Files.move(openFile, file);
            } catch (final IOException e) {
                LOGGER.error(() -> LogUtil.message("Unable to close the event file '{}' a previous run left open: {}",
                        openFile, e.getMessage()), e);
            }
        }
    }

    private void receiveClosedFiles() {
        for (final Path file : list(EventStoreFile.LOG_EXTENSION)) {
            receive(file);
        }
    }

    private List<Path> list(final String suffix) {
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .toList();
        } catch (final IOException e) {
            LOGGER.error(() -> "Unable to list the event directory " + dir + ": " + e.getMessage(), e);
            return List.of();
        }
    }

    private void receive(final Path file) {
        final AttributeMap attributeMap;
        try {
            trimPartialLine(file);
            if (Files.size(file) == 0) {
                // Created for a write that never completed; nothing was acknowledged.
                Files.delete(file);
                return;
            }
            attributeMap = readAttributeMap(file);
            // The request that produced these events was authenticated and filtered under its own
            // elevation, long gone by now; the receiver filters again and needs an identity for it.
            securityContext.asProcessingUser(() ->
                    receiver.receive(Instant.now(), attributeMap, SOURCE, () -> Files.newInputStream(file)));
        } catch (final IOException | RuntimeException e) {
            failed(file, e);
            return;
        }

        try {
            Files.delete(file);
            refusals.remove(file);
        } catch (final IOException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Event file '{}' was received as {} but could not be deleted: {}. It will be received "
                    + "again, as a duplicate, on the next tick.",
                    file, attributeMap.get(StandardHeaderArguments.RECEIPT_ID), e.getMessage()), e);
        }
    }

    /**
     * A refusal - a status the sender would have been told - will not change with time, so it is
     * bounded and then quarantined. Anything else is the store or the queue being unavailable, and
     * the file waits for them.
     */
    private void failed(final Path file, final Exception cause) {
        final boolean refused = cause instanceof StroomStreamException streamException
                                && streamException.getStroomStatusCode().getHttpCode() < 500;
        if (!refused) {
            LOGGER.error(() -> LogUtil.message(
                    "Event file '{}' could not be received and will be tried again: {}",
                    file, LogUtil.exceptionMessage(cause)), cause);
            return;
        }
        final int refusal = refusals.merge(file, 1, Integer::sum);
        if (refusal < MAX_REFUSALS) {
            LOGGER.error(() -> LogUtil.message(
                    "Event file '{}' was refused (refusal {} of {}): {}",
                    file, refusal, MAX_REFUSALS, LogUtil.exceptionMessage(cause)), cause);
        } else if (quarantine(file, refusal, cause)) {
            refusals.remove(file);
        }
    }

    /**
     * A file is written one whole line per synchronous write, so the only damage a power cut can
     * do is a torn last line, and it was never acknowledged.
     */
    private static void trimPartialLine(final Path file) throws IOException {
        try (final RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            final long length = raf.length();
            if (length == 0) {
                return;
            }
            raf.seek(length - 1);
            if (raf.read() == '\n') {
                return;
            }
            long end = length - 1;
            while (end > 0) {
                raf.seek(end - 1);
                if (raf.read() == '\n') {
                    break;
                }
                end--;
            }
            final long keep = end;
            LOGGER.warn(() -> LogUtil.message(
                    "Event file '{}' ends in a partial line, which a previous run never acknowledged; "
                    + "truncating it from {} to {} bytes", file, length, keep));
            raf.setLength(keep);
        }
    }

    /**
     * What the receiver's policy should see: the headers the sender sent with the first event,
     * its feed and type, which the file is keyed on, and its receipt id, so the chain continues.
     */
    private static AttributeMap readAttributeMap(final Path file) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final String firstLine = reader.readLine();
            if (firstLine == null) {
                throw new IOException("Event file is empty");
            }
            final Event event = JsonUtil.readValue(firstLine, Event.class);
            final AttributeMap attributeMap = new AttributeMap();
            NullSafe.list(event.getHeaders()).forEach(header ->
                    attributeMap.put(header.getName(), header.getValue()));
            // The file is plain text whatever the sender said its event was.
            attributeMap.remove(StandardHeaderArguments.COMPRESSION);
            if (event.getFeed() != null) {
                attributeMap.put(StandardHeaderArguments.FEED, event.getFeed());
            }
            if (event.getType() != null) {
                attributeMap.put(StandardHeaderArguments.TYPE, event.getType());
            }
            if (event.getEventId() != null) {
                attributeMap.put(StandardHeaderArguments.RECEIPT_ID, event.getEventId());
            }
            return attributeMap;
        } catch (final RuntimeException e) {
            // Not a file this proxy wrote; the sender of a real one would have been told a status.
            throw new StroomStreamException(StroomStatusCode.INVALID_FORMAT, new AttributeMap(), LogUtil.message(
                    "Unable to read the first event of '{}': {}", file, LogUtil.exceptionMessage(e)));
        }
    }

    /**
     * @return True if the file was moved. Never throws: this is the schedule's only error path, and
     * a file that cannot be moved is no worse off left where it is, to be tried again next tick.
     */
    private boolean quarantine(final Path file, final int refusals, final Exception cause) {
        try {
            ensureDirExists(failedDir);
            Path destination = failedDir.resolve(file.getFileName());
            for (int n = 1; ; n++) {
                try {
                    Files.move(file, destination);
                    break;
                } catch (final FileAlreadyExistsException e) {
                    destination = failedDir.resolve(file.getFileName() + "." + n);
                }
            }
            final Path moved = destination;
            LOGGER.error(() -> LogUtil.message(
                    "Event file '{}' was refused {} times and has been moved to '{}'. It will not be tried "
                    + "again; its data is intact and needs a decision. Last refusal: {}",
                    file, refusals, moved, LogUtil.exceptionMessage(cause)));
            return true;
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Event file '{}' was refused {} times and could not be moved to '{}': {}. The move will be "
                    + "tried again next tick. Last refusal: {}",
                    file, refusals, failedDir, LogUtil.exceptionMessage(e), LogUtil.exceptionMessage(cause)), e);
            return false;
        }
    }

    private static void ensureDirExists(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
