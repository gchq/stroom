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
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceArchiveOperation;
import stroom.planb.impl.db.trace.TraceDb;
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
import stroom.util.shared.PageRequest;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Operation-level tests for {@code TraceArchiveOperation}, driving a real {@link SharedFileStoreShard} and
 * a real {@link SharedFileStorePublisher} against a temp "shared" directory.
 *
 * <p>These exist because the {@code TraceDb}-level tests cannot see this class of bug: they assert what
 * {@code archiveRootedSpans} stages and returns, but the decision about whether to actually publish what
 * was staged lives in the operation. A gate on the wrong value there discards correctly-staged data.
 */
class TestTraceArchiveOperation {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_INDEX = 0;
    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";
    private static final Instant ROOT_START = Instant.parse("2024-01-10T12:00:00.000Z");
    private static final String DAY_LABEL = "2024-01-10";

    @TempDir
    Path tempDir;

    @Mock
    private NodeInfo nodeInfo;

    private Path shared;
    private PlanBPaths planBPaths;
    private PlanBDoc doc;
    private SharedFileStoreShard shard;
    private TraceArchiveOperation operation;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        when(nodeInfo.getThisNodeName()).thenReturn("test-node");

        shared = Files.createDirectories(tempDir.resolve("shared"));
        planBPaths = new PlanBPaths(tempDir.resolve("local_state"));
        doc = buildDoc(shared);

        final PlanBConfig config = PlanBConfig.builder().build();
        shard = new SharedFileStoreShard(BYTE_BUFFERS, BYTE_BUFFER_FACTORY, () -> config, planBPaths,
                doc, SHARD_INDEX, planBPaths.getMergingDir());

        final SharedFileStorePublisher publisher = new SharedFileStorePublisher(
                nodeInfo, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, planBPaths);
        operation = new TraceArchiveOperation(new LocalArchive(publisher, planBPaths));
    }

    /**
     * Regression guard for silent data loss. A trace whose only span is its root removes nothing from the
     * live shard, because only non-root spans are ever deleted — but it still stages a delta that must be
     * published. Gating the push on the removed-span count discarded it, and {@code evictArchivedRoots}
     * then met all its conditions and deleted the trace, so it existed in no queryable store at all.
     */
    @Test
    void publishesARootOnlyTrace_whichRemovesNoSpans() throws IOException {
        insert(rootKey());

        assertThat(operation.run(ctx()))
                .as("staged a bucket, so the operation must report the shard as modified")
                .isTrue();

        assertThat(archivedTraceIds())
                .as("the trace must be in its bucket, not silently dropped")
                .containsExactly(TRACE_A);
    }

    @Test
    void publishesATraceWithChildren() throws IOException {
        insert(rootKey());
        insert(childKey());

        assertThat(operation.run(ctx())).isTrue();
        assertThat(archivedTraceIds()).containsExactly(TRACE_A);
    }

    /** With no rooted traces at all there is nothing staged, so nothing is pushed and no bucket appears. */
    @Test
    void reportsNotModified_whenThereIsNothingToArchive() throws IOException {
        assertThat(operation.run(ctx())).isFalse();
        assertThat(bucketDir()).doesNotExist();
    }

    /** An orphan has no real root, so no bucket can be derived and nothing should be published. */
    @Test
    void doesNotPublishAnOrphanTrace() throws IOException {
        insert(childKey());

        assertThat(operation.run(ctx())).isFalse();
        assertThat(bucketDir()).doesNotExist();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SharedFileStoreOperationContext ctx() {
        return new SharedFileStoreOperationContext(doc, SHARD_INDEX, shard,
                shared.resolve(PlanBConstants.SHARDS_DIR_NAME).resolve(doc.getUuid()), "test-lock");
    }

    private void insert(final SpanKey key) {
        shard.writeWithDb(db -> {
            final TraceDb traceDb = (TraceDb) db;
            traceDb.write(writer -> traceDb.insert(writer, new SpanKV(key, span())));
            traceDb.mergeComplete();
            return null;
        });
    }

    private Path bucketDir() {
        return shared.resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(DAY_LABEL);
    }

    private List<String> archivedTraceIds() {
        try (final TraceDb archive = TraceDb.create(
                bucketDir(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final TracesResultPage page = archive.findTraces(new FindTraceCriteria(
                    new PageRequest(0, 1000), null, null, SimpleDuration.ZERO));
            return page.getValues().stream().map(TraceRoot::getTraceId).sorted().toList();
        }
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
                                        .duration(SimpleDuration.builder()
                                                .time(7).timeUnit(TimeUnit.DAYS).build())
                                        .granularity(ArchivalGranularity.DAY)
                                        .rootCutOff(SimpleDuration.builder()
                                                .time(10).timeUnit(TimeUnit.MINUTES).build())
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
                .startTimeUnixNano(NanoTimeUtil.fromInstant(ROOT_START))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(ROOT_START))
                .insertTime(NanoTimeUtil.fromInstant(ROOT_START))
                .build();
    }
}
