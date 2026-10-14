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

package stroom.planb.impl.dao.trace;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.fs.SharedFileStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * Writes a {@link QueueItem} — the spans, roots and lookup tables of a set of traces, copied out of a
 * trace store into an environment of their own.
 *
 * <p>The environment is built on local disk and only its finished {@code data.mdb} is copied to the
 * shared store, because no LMDB environment is ever opened on the shared mount — an invariant of the
 * whole shared file store, and the reason every other path here copies down, works locally and pushes
 * back. Opening one there would put LMDB's memory-mapped writes and its {@code lock.mdb} on a mount
 * that may not support either.
 *
 * <p>The copy lands under a temporary name and is renamed into place as the last thing that happens,
 * so a consumer never sees a part-written item and a producer that dies mid-write leaves only a
 * temporary directory behind.
 */
public class QueueItemWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueueItemWriter.class);

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Path localBuildDir;

    /**
     * @param localBuildDir a directory on local disk to build items in. Each is built under a name of
     *                      its own and removed once copied, so this holds at most one item per writer
     *                      in flight.
     */
    public QueueItemWriter(final ByteBuffers byteBuffers,
                           final ByteBufferFactory byteBufferFactory,
                           final Path localBuildDir) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.localBuildDir = localBuildDir;
    }

    public QueueItemWriter(final ByteBuffers byteBuffers,
                           final ByteBufferFactory byteBufferFactory,
                           final PlanBPaths planBPaths) {
        this(byteBuffers, byteBufferFactory, planBPaths.getArchiveLocalDir().resolve("queue_build"));
    }

    /**
     * Copies the named traces out of {@code source} into a new item under {@code targetDir}.
     *
     * @param source    the store to read from, which must hold the spans and roots of every trace
     *                  named. The caller decides which traces those are.
     * @param traceIds  raw 16 byte trace ids.
     * @param targetDir the directory items are written into; created where absent.
     * @param orderKey  when the producer released these traces, as epoch milliseconds. It leads the
     *                  item's name and is stored inside it, and is what a consumer applies items in
     *                  the order of.
     * @param sourceUuid the trace store these traces came from, stored in the item so that what is
     *                  learnt from them can say where they came from. Null where the caller has no
     *                  identity to give.
     * @return the finished item's directory, or empty where there was nothing to send. Writing an
     * empty item would cost a whole environment to say so.
     * @throws IOException where the item could not be written. Nothing is left behind that a consumer
     *                     would pick up, so a caller that can retry loses nothing by doing so.
     */
    public Optional<Path> write(final TraceDb source,
                                final Collection<byte[]> traceIds,
                                final Path targetDir,
                                final long orderKey,
                                final String sourceUuid) throws IOException {
        if (traceIds.isEmpty()) {
            return Optional.empty();
        }

        final String name = QueueItem.newName(orderKey);
        // The name carries a uuid, so no two writers can pick the same local directory.
        final Path localDir = localBuildDir.resolve(name);
        final Path tmpDir = targetDir.resolve(QueueItem.tmpName(name));
        final Path itemDir = targetDir.resolve(name);
        try {
            build(source, traceIds, localDir, orderKey, sourceUuid);
            publish(localDir, tmpDir, itemDir);
            return Optional.of(itemDir);

        } catch (final IOException | RuntimeException e) {
            deleteQuietly(tmpDir);
            throw e;
        } finally {
            deleteQuietly(localDir);
        }
    }

    private void build(final TraceDb source,
                       final Collection<byte[]> traceIds,
                       final Path localDir,
                       final long orderKey,
                       final String sourceUuid) throws IOException {
        Files.createDirectories(localDir);

        try (final TraceDb item = TraceDb.create(
                localDir,
                byteBuffers,
                byteBufferFactory,
                QueueItem.doc(),
                false,
                QueueItem.HAS_SECONDARY_INDEXES)) {
            source.copyTracesTo(item, traceIds);
            QueueItem.writeInfo(item.getEnv(), orderKey, sourceUuid);
        }

        // An artefact of having had the environment open, and never valid anywhere else: a stale
        // one mapped by another process is what makes a shared lock.mdb dangerous.
        Files.deleteIfExists(localDir.resolve(PlanBConstants.LOCK_FILE_NAME));
    }

    private static void publish(final Path localDir,
                                final Path tmpDir,
                                final Path itemDir) throws IOException {
        Files.createDirectories(tmpDir);
        Files.copy(localDir.resolve(PlanBConstants.DATA_FILE_NAME),
                tmpDir.resolve(PlanBConstants.DATA_FILE_NAME));

        // What says the directory is finished, and the only thing the merge looks for. Written while
        // the directory is still temporary, so it is there the moment the move makes it visible.
        Files.writeString(tmpDir.resolve(PlanBConstants.VERSION_FILE_NAME),
                Instant.now().toString(), StandardCharsets.UTF_8);

        // Last, and atomic: a failure above leaves a temporary directory to be tidied rather than an
        // item half handed over.
        SharedFileStore.moveWithAtomicFallback(tmpDir, itemDir);
    }

    private static void deleteQuietly(final Path dir) {
        try (final var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException e) {
                    LOGGER.debug(() -> "Could not delete " + path + ": " + e.getMessage(), e);
                }
            });
        } catch (final IOException e) {
            LOGGER.debug(() -> "Could not clean up " + dir + ": " + e.getMessage(), e);
        }
    }
}
