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

package stroom.pathways.impl;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.dao.trace.QueueItem;
import stroom.planb.impl.dao.trace.QueueItemWriter;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.fs.MergeContext;
import stroom.planb.impl.fs.OperationMarker;
import stroom.planb.impl.fs.ShardQueue;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.security.api.SecurityContext;
import stroom.util.metrics.Metrics;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The consumer's operational behaviour, with nothing applied: which shards it locks, how much it
 * takes on per hold, what it does when interrupted, and what it deletes.
 *
 * <p>The applying itself is a counter here, which is the point — everything that can go wrong
 * operationally can be settled before a model exists to go wrong with it.
 */
class TestPathwaysProcessor {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_COUNT = 4;
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String SPAN_NAME = "GET /orders";

    @TempDir
    Path tempDir;

    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private PathwaysShardStore shardStore;
    @Mock
    private MessageReceiverFactory messageReceiverFactory;

    private static final OperationMarker NARROWED_MARKER =
            new OperationMarker(PlanBConstants.NARROWED_LAST_FILE_NAME);

    private Path pathwaysShared;
    private Path narrowedMarkers;
    private Path bucketDir;
    private PlanBDoc tracesDoc;
    private PathwaysDoc pathwaysDoc;
    private PathwaysProcessor processor;
    private int nextTraceId;
    private Path lastLocalDir;
    private boolean lastChanged;
    private final Map<Integer, Path> localDirs = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        pathwaysShared = Files.createDirectories(tempDir.resolve("pathways_shared"));
        narrowedMarkers = Files.createDirectories(tempDir.resolve("pathways_shards"));

        pathwaysDoc = PathwaysDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test Pathways")
                .sharedFileStore(new SharedFileStoreSettings(SHARD_COUNT, pathwaysShared.toString()))
                .build();

        // Run the supplied work rather than mocking out the thing under test.
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityContext).asProcessingUser(any());
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(clusterLockService).tryLock(anyString(), any(Runnable.class));

        // Hand the work a local directory and run it, keeping one per shard so a model accumulates
        // across holds the way the real store makes it. TestPathwaysShardStore covers the copying.
        doAnswer(invocation -> {
            final int shardIndex = invocation.getArgument(1, Integer.class);
            Path localDir = localDirs.get(shardIndex);
            if (localDir == null) {
                localDir = Files.createTempDirectory(tempDir, "localShard" + shardIndex);
                localDirs.put(shardIndex, localDir);
            }
            lastLocalDir = localDir;
            lastChanged = invocation.getArgument(2, Predicate.class).test(localDir);
            return lastChanged;
        }).when(shardStore).withShard(any(), anyInt(), any());

        // Where the marker saying when each shard was last narrowed lives. Every shard is marked as
        // narrowed just now, so nothing is due unless a test says so — an unmarked shard is due, which
        // is what makes a fresh install narrow everything once.
        doReturn(Optional.of(narrowedMarkers)).when(shardStore).sharedDocDir(any());
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            NARROWED_MARKER.recordRun(narrowedMarkers, shard);
        }

        tracesDoc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-traces")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder().build())
                .build();
        bucketDir = Files.createDirectories(tempDir.resolve("bucket"));

        processor = new PathwaysProcessor(
                new MutationLog(BYTE_BUFFERS, new PathwaySerde(BYTE_BUFFER_FACTORY)),
                shardStore, messageReceiverFactory,
                new PathwaySerde(BYTE_BUFFER_FACTORY),
                new PathwayNarrower(new PathwaySerde(BYTE_BUFFER_FACTORY),
                        new MutationLog(BYTE_BUFFERS, new PathwaySerde(BYTE_BUFFER_FACTORY)),
                        BYTE_BUFFERS),
                BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                () -> new MetricRegistry());
    }

    @Test
    void anEmptyShardIsNeverLocked() {
        runPass();

        verify(clusterLockService, never()).tryLock(anyString(), any(Runnable.class));
    }

    @Test
    void onlyShardsWithWorkAreLocked() throws IOException {
        final int shard = 2;
        writeItem(shard, 1_000L, 1);

        runPass();

        verify(clusterLockService).tryLock(
                eq(PathwaysProcessor.lockName(pathwaysDoc.getUuid(), shard)), any(Runnable.class));
        verify(clusterLockService, never()).tryLock(
                eq(PathwaysProcessor.lockName(pathwaysDoc.getUuid(), 0)), any(Runnable.class));
    }

    @Test
    void appliedItemsAreDeleted() throws IOException {
        writeItem(1, 1_000L, 1);
        writeItem(1, 2_000L, 1);
        assertThat(itemsIn(1)).hasSize(2);

        runPass();

        assertThat(itemsIn(1)).as("an applied item is taken out of the queue").isEmpty();
    }

    @Test
    void theCountBoundLeavesTheRestForNextTime() throws IOException {
        for (int i = 0; i <= PathwaysProcessor.MAX_ITEMS_PER_HOLD; i++) {
            writeItem(1, 1_000L + i, 1);
        }
        final int before = itemsIn(1).size();

        runPass();

        assertThat(itemsIn(1))
                .as("one hold takes at most the bound, and the rest stay queued")
                .hasSize(before - PathwaysProcessor.MAX_ITEMS_PER_HOLD);
    }

    @Test
    void itemsAreTakenOldestFirst() throws IOException {
        // One over the bound, so exactly the newest is left behind.
        final List<Long> orderKeys = new ArrayList<>();
        for (int i = 0; i <= PathwaysProcessor.MAX_ITEMS_PER_HOLD; i++) {
            orderKeys.add(1_000L + i);
            writeItem(1, 1_000L + i, 1);
        }

        runPass();

        final List<Path> left = itemsIn(1);
        assertThat(left).hasSize(1);
        assertThat(QueueItem.orderKeyOf(left.getFirst()))
                .as("the newest item is the one left behind")
                .isEqualTo(orderKeys.getLast());
    }

    /**
     * Covers an interrupt that arrives before the first item. One arriving part way through is the
     * composition of this and {@link #appliedItemsAreDeleted()} — that only what was applied is
     * deleted — because there is no seam to interrupt the loop from outside without racing it.
     */
    @Test
    void anInterruptBeforeTheBatchConsumesNothing() throws IOException {
        writeItem(1, 1_000L, 1);
        writeItem(1, 2_000L, 1);

        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            try {
                invocation.getArgument(1, Runnable.class).run();
            } finally {
                Thread.interrupted();
            }
            return null;
        }).when(clusterLockService).tryLock(anyString(), any(Runnable.class));

        runPass();

        assertThat(itemsIn(1))
                .as("an interrupted process must not delete what it never applied")
                .hasSize(2);
    }

    @Test
    void anUnreadableItemIsSetAsideAndTheRestStillApply() throws IOException {
        writeItem(1, 1_000L, 1);
        corruptItem(1, 2_000L);
        writeItem(1, 3_000L, 1);

        runPass();

        assertThat(itemsIn(1)).as("the readable items were applied and removed").isEmpty();
        final Path quarantine = shardDir(1).resolve(ShardQueue.QUARANTINE_DIR_NAME);
        assertThat(quarantine).isDirectory();
        try (final Stream<Path> stream = Files.list(quarantine)) {
            assertThat(stream.toList()).as("the bad item is kept, not destroyed").hasSize(1);
        }
    }

    @Test
    void aTraceBecomesAPathwayInTheShardsModel() throws IOException {
        writeItem(1, 1_000L, 1);

        runPass();

        try (final PathwaysDb db = PathwaysDb.create(lastLocalDir, BYTE_BUFFERS, true)) {
            final List<String> names = new ArrayList<>();
            db.getPathways().iterate((key, val) ->
                    names.add(ByteBufferUtils.toString(key)));
            assertThat(names)
                    .as("the root span's operation name keys the pathway it built")
                    .containsExactly(SPAN_NAME);
        }
    }

    @Test
    void aTraceAlreadyAppliedDoesNotMakeTheModelWorthPushing() throws IOException {
        final String traceId = "d".repeat(32);
        writeItem(1, 1_000L, traceId);
        runPass();
        assertThat(lastChanged).as("the first sighting builds a pathway").isTrue();

        // The same trace again, into the model that already holds it — which is what at-least-once
        // delivery makes routine. Nothing changes, so there is nothing worth copying back up.
        writeItem(1, 2_000L, traceId);
        runPass();

        assertThat(lastChanged)
                .as("re-applying a trace this shard has seen is not worth a push")
                .isFalse();
    }

    @Test
    void aQuietShardIsStillWorkedWhenItIsDueANarrowing() throws IOException {
        // Nothing queued anywhere, so nothing to apply. Shard 2 has no marker, which is how a shard
        // that has never been narrowed presents.
        Files.delete(narrowedMarkers.resolve("0002").resolve(PlanBConstants.NARROWED_LAST_FILE_NAME));

        runPass();

        verify(clusterLockService).tryLock(
                eq(PathwaysProcessor.lockName(pathwaysDoc.getUuid(), 2)), any(Runnable.class));
        verify(clusterLockService, never()).tryLock(
                eq(PathwaysProcessor.lockName(pathwaysDoc.getUuid(), 1)), any(Runnable.class));
    }

    @Test
    void aShardNarrowedIsNotNarrowedAgainUntilTheMarkerIsOldEnough() throws IOException {
        Files.delete(narrowedMarkers.resolve("0002").resolve(PlanBConstants.NARROWED_LAST_FILE_NAME));
        runPass();

        // The pass stamped the marker, so the next one has no reason to open the shard.
        clearInvocations(clusterLockService);
        runPass();

        verify(clusterLockService, never()).tryLock(anyString(), any(Runnable.class));
    }

    @Test
    void aContendedShardIsSkipped() throws IOException {
        writeItem(1, 1_000L, 1);
        // tryLock returning without running is how a lock held by another node presents.
        doAnswer(invocation -> null).when(clusterLockService).tryLock(anyString(), any(Runnable.class));

        runPass();

        assertThat(itemsIn(1)).as("someone else's turn, so nothing is consumed").hasSize(1);
    }

    @Test
    void aDocumentWithNoSharedFileStoreIsSkipped() {
        final PathwaysDoc unconfigured = PathwaysDoc.builder()
                .uuid(pathwaysDoc.getUuid())
                .name("Unconfigured")
                .build();

        assertThat(processor.shardsWithOwnWork(unconfigured))
                .as("nothing was ever written for it, so there is nothing to lock a shard for")
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    /**
     * One pass over the document, done the way Plan B's merge does it: ask the strategy which shards
     * have work, then take each one's lock and run what it gave back.
     *
     * <p>Here rather than calling something on the processor, because there is nothing to call — the
     * processor no longer drives a pass of its own, and a test that drove one would be testing a path
     * nothing uses.
     */
    private void runPass() {
        processor.shardsWithOwnWork(pathwaysDoc).forEach(shardIndex ->
                securityContext.asProcessingUser(() ->
                        clusterLockService.tryLock(processor.lockName(pathwaysDoc, shardIndex),
                                () -> processor.mergeShard(
                                        new MergeContext(pathwaysDoc, shardIndex,
                                                processor.lockName(pathwaysDoc, shardIndex), false),
                                        List.of()))));
    }

    private void writeItem(final int shard, final long orderKey, final String traceIdHex)
            throws IOException {
        writeItem(shard, orderKey, 1, traceIdHex);
    }

    private void writeItem(final int shard, final long orderKey, final int traceCount)
            throws IOException {
        writeItem(shard, orderKey, traceCount, null);
    }

    // A real queue item, written by the real writer out of a real bucket, so that reading it back
    // exercises the span and lookup decoding rather than a stand-in. Naming the trace lets a test
    // deliver the same one twice, which is what at-least-once delivery does.
    private void writeItem(final int shard,
                           final long orderKey,
                           final int traceCount,
                           final String traceIdHex) throws IOException {
        final List<byte[]> traceIds = new ArrayList<>();
        try (final TraceDb bucket = TraceDb.create(
                bucketDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, tracesDoc, false)) {
            bucket.write(writer -> {
                for (int i = 0; i < traceCount; i++) {
                    final String traceId = traceIdHex != null
                            ? traceIdHex
                            : String.format("%032x", ++nextTraceId);
                    traceIds.add(HexStringUtil.decode(traceId));
                    bucket.insert(writer, new SpanKV(
                            SpanKey.builder()
                                    .traceId(traceId)
                                    .spanId(ROOT_SPAN)
                                    .parentSpanId("")
                                    .build(),
                            SpanValue.builder()
                                    .name(SPAN_NAME)
                                    .startTimeUnixNano(NanoTimeUtil.fromInstant(Instant.now()))
                                    .endTimeUnixNano(NanoTimeUtil.fromInstant(Instant.now()))
                                    .insertTime(NanoTimeUtil.fromInstant(Instant.now()))
                                    .build()));
                }
            });
            final Path dir = Files.createDirectories(shardDir(shard));
            new QueueItemWriter(BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                    Files.createDirectories(pathwaysShared.resolveSibling("local_build")))
                    .write(bucket, traceIds, dir, orderKey, null);
        }
    }

    // A directory that looks like an item but holds no LMDB environment, so opening it fails the way
    // a truncated or half-copied item would.
    private void corruptItem(final int shard, final long orderKey) throws IOException {
        final Path dir = Files.createDirectories(shardDir(shard).resolve(QueueItem.newName(orderKey)));
        Files.writeString(dir.resolve(PlanBConstants.DATA_FILE_NAME), "not an lmdb file");
    }

    // Through ShardQueue, so this cannot drift from where the code under test reads.
    private Path shardDir(final int shard) {
        return ShardQueue.of(pathwaysDoc.getSharedFileStore(), pathwaysDoc.getUuid(), shard).dir();
    }

    private List<Path> itemsIn(final int shard) throws IOException {
        final Path dir = shardDir(shard);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream.filter(QueueItem::isItem).sorted(QueueItem.BY_ORDER_KEY).toList();
        }
    }
}
