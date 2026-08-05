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

package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceArchiveOperation;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.fs.ArchiveOperation;
import stroom.planb.impl.fs.LocalArchive;
import stroom.planb.impl.fs.SharedFileStoreOperationContext;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.SharedFileStoreShard;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Evidence that {@code TraceDb.archiveOldData}'s root pass cannot fire for a rooted trace, and so is dead
 * weight that can be deleted.
 *
 * <p>Two operations already own a rooted trace's whole lifecycle: {@code archiveRootedSpans} copies its
 * spans into the archive every merge cycle, and {@code evictArchivedRoots} removes the root once past the
 * root cut-off. Both live in {@link TraceArchiveOperation}, priority 150, which
 * {@code SharedFileStoreMergeProcessor.mergeShard} runs before {@link ArchiveOperation} at priority 200 —
 * inside the same cluster lock. So in any cycle where both age gates are open, eviction happens first and
 * the root pass finds nothing left to select.
 *
 * <p>The control case runs {@link ArchiveOperation} on its own. It must archive the root, proving the
 * assertion is not vacuous — the root pass really does select this trace when nothing has been there first.
 */
class TestArchiveOldDataRootPassIsDead {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_INDEX = 0;
    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    /** Well in the past, so every age gate is wide open and neither operation is waiting on a clock. */
    private static final Instant LONG_AGO = Instant.parse("2024-01-10T12:00:00.000Z");

    @TempDir
    Path tempDir;

    @Mock
    private NodeInfo nodeInfo;

    private Path shared;
    private PlanBPaths planBPaths;
    private PlanBDoc doc;
    private SharedFileStoreShard shard;
    private TraceArchiveOperation traceOperation;
    private ArchiveOperation archiveOperation;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(nodeInfo.getThisNodeName()).thenReturn("test-node");

        shared = Files.createDirectories(tempDir.resolve("shared"));
        planBPaths = new PlanBPaths(tempDir.resolve("local_state"));
        doc = buildDoc(shared);

        final PlanBConfig config = PlanBConfig.builder().build();
        shard = new SharedFileStoreShard(BYTE_BUFFERS, BYTE_BUFFER_FACTORY, () -> config, planBPaths,
                doc, SHARD_INDEX, planBPaths.getMergingDir());

        final SharedFileStorePublisher publisher = new SharedFileStorePublisher(
                nodeInfo, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, planBPaths);
        final LocalArchive localArchive = new LocalArchive(publisher, planBPaths);
        traceOperation = new TraceArchiveOperation(localArchive);
        archiveOperation = new ArchiveOperation(localArchive);
    }

    /**
     * The claim. After {@link TraceArchiveOperation} has run, the live store holds no trace root and no
     * spans, so {@link ArchiveOperation} — whose only remaining job for a rooted trace is the root pass —
     * has nothing to archive and reports no work.
     */
    @Test
    void rootPassFindsNothing_afterTheTraceOperationHasRun() throws IOException {
        ingestRootedTrace();

        assertThat(traceOperation.run(ctx()))
                .as("the trace operation archives the trace and evicts its root")
                .isTrue();
        assertThat(liveRootSpanPresent()).as("no root left in the live store").isFalse();
        assertThat(liveSpanCount()).as("no spans left in the live store").isZero();

        assertThat(archiveOperation.run(ctx()))
                .as("so archiveOldData's root pass has nothing to select")
                .isFalse();
    }

    /**
     * The control. Run alone, {@link ArchiveOperation} does archive the trace — so the assertion above is
     * about ordering, not about the trace being unarchivable in the first place.
     */
    @Test
    void rootPassDoesFindTheTrace_whenTheTraceOperationHasNotRun() throws IOException {
        ingestRootedTrace();

        assertThat(archiveOperation.run(ctx()))
                .as("on its own the root pass selects and archives the trace")
                .isTrue();
        assertThat(liveRootSpanPresent()).as("and removes its root").isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SharedFileStoreOperationContext ctx() {
        return new SharedFileStoreOperationContext(doc, SHARD_INDEX, shard,
                shared.resolve("shards").resolve(doc.getUuid()), "test-lock");
    }

    /**
     * Delivers a root span plus a child the way ingest does — via {@code merge} from a batch env, which is
     * what queues the root rebuild that gives the trace its trace-roots entry and stats.
     */
    private void ingestRootedTrace() throws IOException {
        final Path batch = Files.createDirectories(tempDir.resolve("batch"));
        try (final TraceDb db = TraceDb.create(batch, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span()));
                db.insert(writer, new SpanKV(childKey(), span()));
            });
        }
        shard.merge(batch);
        shard.mergeComplete();
    }

    /**
     * The root span, as a proxy for the trace-roots entry — {@code evictArchivedRoots} removes both
     * together, and the holding shard is opened without sort indexes so {@code findTraces} cannot be used
     * on it.
     */
    private boolean liveRootSpanPresent() {
        return shard.writeWithDb(db -> ((TraceDb) db).get(rootKey()) != null);
    }

    private long liveSpanCount() {
        return shard.writeWithDb(db -> ((TraceDb) db).count());
    }

    private static PlanBDoc buildDoc(final Path sharedPath) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .sharedFileStore(new SharedFileStoreSettings(
                                1,
                                sharedPath.toAbsolutePath().toString(),
                                new ArchivalSettings.Builder()
                                        .enabled(true)
                                        // Both gates are far in the past relative to LONG_AGO, so neither
                                        // operation is held up by a clock and only ordering decides.
                                        .duration(SimpleDuration.builder()
                                                .time(2).timeUnit(TimeUnit.MINUTES).build())
                                        .rootCutOff(SimpleDuration.builder()
                                                .time(1).timeUnit(TimeUnit.MINUTES).build())
                                        .granularity(ArchivalGranularity.DAY)
                                        .build()))
                        .build())
                .build();
    }

    private static SpanKey rootKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanKey childKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId(ROOT_SPAN).spanId(CHILD_SPAN).build();
    }

    private static SpanValue span() {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(LONG_AGO))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(LONG_AGO))
                .insertTime(NanoTimeUtil.fromInstant(LONG_AGO))
                .build();
    }
}
