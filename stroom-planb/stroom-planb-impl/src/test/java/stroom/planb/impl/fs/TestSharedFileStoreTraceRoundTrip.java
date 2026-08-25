/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.DefaultBatchDestination;
import stroom.planb.impl.dao.PlanBStreamWriter;
import stroom.planb.impl.dao.PlanBStreamWriterFactory;
import stroom.planb.impl.dao.ShardKeyRouter;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.rest.FileTransferClient;
import stroom.planb.impl.rest.RestPartDestination;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.HoldingAreaSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Joins up the shared file store path for trace stores, which is the only store type production
 * gives a {@link MergeStrategy} — {@code PathwaysModule} binds one for {@code TRACE} and nothing
 * binds any other, so every other type is skipped by {@link SharedFileStoreMergeProcessor}.
 * <p>
 * Spans are written through {@link PlanBStreamWriter}, land in the shared store's processing dir via
 * {@link SharedFileStorePartDestination}, are merged through the holding shard and drained into a
 * date-labelled archive bucket, and are then read back the way a query reads them: through
 * {@link ArchiveShardLocator} and {@link ShardManager#getArchive}.
 * <p>
 * {@code TestSharedFileStoreMerge} covers the same steps for a {@code STATE} store, but that
 * configuration never occurs in production and it reads the published LMDB file directly rather than
 * through the query path.
 */
class TestSharedFileStoreTraceRoundTrip {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_COUNT = 2;
    private static final String NODE_NAME = "test-node";

    // 16-byte trace id (32 hex chars) and 8-byte span ids (16 hex chars).
    private static final String TRACE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";
    private static final String LATE_SPAN = "3333333333333333";

    /** Well in the past, so a zero wait for data publishes it and its bucket label is predictable. */
    private static final Instant ROOT_START = Instant.parse("2024-01-10T09:00:00.000Z");
    private static final String EXPECTED_BUCKET = "2024-01-10";

    @TempDir
    Path tempDir;

    private PlanBPaths planBPaths;
    private PlanBConfig planBConfig;
    private Path sharedRootDir;

    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private TaskContextFactory taskContextFactory;
    @Mock
    private PlanBDocCache planBDocCache;
    @Mock
    private FileTransferClient fileTransferClient;
    @Mock
    private ExecutorProvider executorProvider;

    private AutoCloseable mocks;
    private ShardManager shardManager;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        planBPaths = new PlanBPaths(tempDir.resolve("local_state"));
        sharedRootDir = tempDir.resolve("shared_store");
        Files.createDirectories(sharedRootDir);

        planBConfig = PlanBConfig.builder()
                .nodeList(Collections.singletonList(NODE_NAME))
                .build();

        when(executorProvider.get()).thenReturn(Runnable::run);
        when(nodeInfo.getThisNodeName()).thenReturn(NODE_NAME);

        // The processor passes the current context straight to childContext, and the childContext
        // stub below matches any(TaskContext.class), which does not match null.
        when(taskContextFactory.current()).thenReturn(Mockito.mock(TaskContext.class));
        doAnswer(invocation -> {
            final Consumer<TaskContext> consumer = invocation.getArgument(2);
            return (Runnable) () -> consumer.accept(Mockito.mock(TaskContext.class));
        }).when(taskContextFactory).childContext(any(TaskContext.class), any(String.class), any(Consumer.class));

        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));

        // Take every lock, running the merge on the calling thread.
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).tryLock(any(String.class), any(Runnable.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (shardManager != null) {
            shardManager.closeAll();
        }
        if (mocks != null) {
            mocks.close();
        }
        FileUtil.deleteDir(tempDir);
    }

    /**
     * The whole path, for the configuration production actually runs: spans written by a stream end
     * up in an archive bucket labelled by their root's start day, and a query reading through
     * {@link ArchiveShardLocator} finds the trace with both its spans.
     * <p>
     * The store is configured to wait a day for data, which does not hold this trace back — the wait
     * applies to a trace with no real root yet, not to one that already has its root.
     */
    @Test
    void traceReachesTheArchiveAndIsReadableThroughTheQueryPath() throws IOException {
        final PlanBDoc doc = doc();
        register(doc);

        writeStream(doc, writer -> {
            writer.addSpanValue(doc, new SpanKV(rootKey(), span(ROOT_START)));
            writer.addSpanValue(doc, new SpanKV(childKey(CHILD_SPAN), span(ROOT_START)));
        });

        // Both spans share a trace id, so both route to the same shard.
        final int shardIndex = shardIndexFor(TRACE_ID);
        assertThat(processingDir(doc).resolve(PlanBConstants.formatShardIndex(shardIndex)))
                .as("the stream's part reached the shared store's processing dir")
                .exists();

        merge(doc);

        final List<ArchiveShardRef> refs = findShards(doc, shardIndex);
        assertThat(refs.stream().map(ArchiveShardRef::dateLabel))
                .as("one bucket, labelled by the root's start day")
                .containsExactly(EXPECTED_BUCKET);

        assertThat(spanCountInArchive(doc, shardIndex, refs.getFirst()))
                .as("root and child both readable through the query path")
                .isEqualTo(2);
    }

    /**
     * What the wait for data actually holds back: a trace whose root has not arrived. Its root is
     * synthesized, so publishing it would bucket it under the wrong time and split it from the spans
     * that follow. It waits in the holding shard, invisible to queries, until the real root turns up
     * in a later stream — and then the whole trace is published together.
     */
    @Test
    void anOrphanIsHeldUntilItsRootArrives() throws IOException {
        final PlanBDoc doc = doc();
        register(doc);

        // A child with no root, recent enough to be inside the one day wait.
        final Instant justNow = Instant.now();
        writeStream(doc, writer ->
                writer.addSpanValue(doc, new SpanKV(childKey(CHILD_SPAN), span(justNow))));
        merge(doc);

        final int shardIndex = shardIndexFor(TRACE_ID);
        assertThat(findShards(doc, shardIndex))
                .as("no real root yet, so nothing is served")
                .isEmpty();
        // Held, not lost.
        assertThat(holdingDir(doc).resolve(PlanBConstants.formatShardIndex(shardIndex))
                .resolve(PlanBConstants.DATA_FILE_NAME))
                .as("still in the holding shard")
                .exists();

        // The root arrives in a later stream.
        writeStream(doc, writer ->
                writer.addSpanValue(doc, new SpanKV(rootKey(), span(justNow))));
        merge(doc);

        final List<ArchiveShardRef> refs = findShards(doc, shardIndex);
        assertThat(refs).as("published once the root arrived").hasSize(1);
        assertThat(spanCountInArchive(doc, shardIndex, refs.getFirst()))
                .as("the held child published with its root, in one bucket")
                .isEqualTo(2);
    }

    /**
     * A bucket is merged into rather than replaced, so a span arriving in a later stream joins the
     * spans already published for its trace rather than displacing them.
     */
    @Test
    void laterStreamAddsToTheTraceAlreadyInTheBucket() throws IOException {
        final PlanBDoc doc = doc();
        register(doc);

        writeStream(doc, writer -> {
            writer.addSpanValue(doc, new SpanKV(rootKey(), span(ROOT_START)));
            writer.addSpanValue(doc, new SpanKV(childKey(CHILD_SPAN), span(ROOT_START)));
        });
        merge(doc);

        writeStream(doc, writer ->
                writer.addSpanValue(doc, new SpanKV(childKey(LATE_SPAN), span(ROOT_START))));
        merge(doc);

        final int shardIndex = shardIndexFor(TRACE_ID);
        final List<ArchiveShardRef> refs = findShards(doc, shardIndex);
        assertThat(refs.stream().map(ArchiveShardRef::dateLabel))
                .as("still one bucket, not one per stream")
                .containsExactly(EXPECTED_BUCKET);

        assertThat(spanCountInArchive(doc, shardIndex, refs.getFirst()))
                .as("the late span joined the two already published")
                .isEqualTo(3);
    }

    private void writeStream(final PlanBDoc doc, final Consumer<PlanBStreamWriter> work) {
        final PlanBStreamWriterFactory factory = new PlanBStreamWriterFactory(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                planBPaths,
                new DefaultBatchDestination(),
                new SharedFileStorePartDestination(),
                new RestPartDestination(fileTransferClient));
        try (final PlanBStreamWriter writer = factory.createWriter(
                Meta.builder().id(System.nanoTime()).build())) {
            work.accept(writer);
        }
    }

    private void merge(final PlanBDoc doc) {
        when(planBDocCache.getAll()).thenReturn(List.of(doc));

        final SharedFileStorePublisher publisher =
                new SharedFileStorePublisher(nodeInfo, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, planBPaths);
        final MergeStrategy holdingStrategy = new HoldingAreaMergeStrategy(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                () -> planBConfig,
                planBPaths,
                publisher,
                new LocalArchive(publisher, planBPaths));
        new SharedFileStoreMergeProcessor(
                clusterLockService,
                () -> planBConfig,
                securityContext,
                taskContextFactory,
                planBDocCache,
                Map.of(StateType.TRACE, holdingStrategy))
                .merge();
    }

    /**
     * Reads the trace back the way {@code AbstractTracesStore} does, through the shard manager's
     * archive reader rather than by opening the published file directly.
     */
    private int spanCountInArchive(final PlanBDoc doc,
                                   final int shardIndex,
                                   final ArchiveShardRef ref) {
        final Integer count = shardManager().getArchive(doc, shardIndex, ref, reader -> {
            if (!(reader instanceof final TraceDb traceDb)) {
                throw new IllegalStateException("Unexpected reader: " + reader);
            }
            return traceDb.findTrace(HexStringUtil.decode(TRACE_ID))
                    .map(TestSharedFileStoreTraceRoundTrip::spanCount)
                    .orElse(null);
        });
        assertThat(count).as("trace found in the archive bucket").isNotNull();
        return count;
    }

    private List<ArchiveShardRef> findShards(final PlanBDocument doc, final int shardIndex) {
        return new ArchiveShardLocator().findRelevantShards(
                doc, shardIndex, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private ShardManager shardManager() {
        if (shardManager == null) {
            shardManager = new ShardManager(
                    BYTE_BUFFERS,
                    BYTE_BUFFER_FACTORY,
                    planBDocCache,
                    null,
                    nodeInfo,
                    () -> planBConfig,
                    planBPaths,
                    fileTransferClient,
                    taskContextFactory,
                    executorProvider,
                    Map::of);
        }
        return shardManager;
    }

    private void register(final PlanBDoc doc) {
        when(planBDocCache.get(doc.getName())).thenReturn(doc);
    }

    private int shardIndexFor(final String traceId) {
        return ShardKeyRouter.computeShardIndex(traceId, SHARD_COUNT);
    }

    private Path processingDir(final PlanBDocument doc) {
        return sharedRootDir.resolve(PlanBConstants.PROCESSING_DIR_NAME).resolve(doc.getUuid());
    }

    private Path holdingDir(final PlanBDocument doc) {
        return sharedRootDir.resolve(PlanBConstants.HOLDING_DIR_NAME).resolve(doc.getUuid());
    }

    private PlanBDoc doc() {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("trace_store")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .sharedFileStore(new SharedFileStoreSettings(
                                SHARD_COUNT, sharedRootDir.toAbsolutePath().toString()))
                        .holdingArea(waitOneDayForData())
                        .build())
                .build();
    }

    private HoldingAreaSettings waitOneDayForData() {
        return new HoldingAreaSettings.Builder()
                .maxWaitForData(SimpleDuration.builder().time(1).timeUnit(TimeUnit.DAYS).build())
                .build();
    }

    private static SpanKey rootKey() {
        return SpanKey.builder().traceId(TRACE_ID).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanKey childKey(final String spanId) {
        return SpanKey.builder().traceId(TRACE_ID).parentSpanId(ROOT_SPAN).spanId(spanId).build();
    }

    private static SpanValue span(final Instant time) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(time))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(time))
                .insertTime(NanoTimeUtil.fromInstant(time))
                .build();
    }

    private static int spanCount(final Trace trace) {
        return trace.getParentSpanIdMap().values().stream().mapToInt(List::size).sum();
    }
}
