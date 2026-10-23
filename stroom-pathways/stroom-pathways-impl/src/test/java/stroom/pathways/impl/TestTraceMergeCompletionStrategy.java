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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStore;
import stroom.docstore.api.DocumentStoreRegistry;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.ShardKeyRouter;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.QueueItem;
import stroom.planb.impl.dao.trace.QueueItemReader;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.fs.ShardQueue;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Whether finished traces reach the Pathways document a trace store is linked to.
 *
 * <p>Driven through {@code pushArchive}, the real entry point, because the two things most likely to
 * be wrong are only true there: the hand-over condition fires once per trace, and it fires against a
 * throwaway local copy of the bucket that is published afterwards. Calling the hand-over directly
 * would prove neither.
 */
class TestTraceMergeCompletionStrategy {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_COUNT = 8;
    private static final String DAY_LABEL = "2024-01-10";
    private static final Instant DAY = Instant.parse("2024-01-10T09:00:00.000Z");
    private static final String ROOT_SPAN = "1111111111111111";

    private static final String TRACE_A = "a".repeat(32);
    private static final String TRACE_B = "b".repeat(32);
    private static final String NAME_A = "GET /orders/{id}";
    private static final String NAME_B = "FetchNewTasks.run";
    /**
     * Over the 32 byte threshold, so its span value references the lookup table instead of holding
     * the name inline. That sends it through {@code insert} when the bucket merges, rather than a
     * direct put — a different path with different side effects.
     */
    private static final String NAME_LONG = "PlanBSharedFileStoreMergeRunnable.run";

    @TempDir
    Path tempDir;

    @Mock
    private DocumentStoreRegistry documentStoreRegistry;
    @Mock
    private DocumentStore<PathwaysDoc> pathwaysStore;

    private Path traceShared;
    private Path pathwaysShared;
    private PathwaysDoc pathwaysDoc;
    private TracesDoc linkedDoc;
    private SharedFileStorePublisher publisher;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        traceShared = Files.createDirectories(tempDir.resolve("trace_shared"));
        pathwaysShared = Files.createDirectories(tempDir.resolve("pathways_shared"));

        pathwaysDoc = PathwaysDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test Pathways")
                .sharedFileStore(new SharedFileStoreSettings(SHARD_COUNT, pathwaysShared.toString()))
                .build();
        doReturn(pathwaysStore).when(documentStoreRegistry).getDocumentStore(PathwaysDoc.TYPE);
        doReturn(pathwaysDoc).when(pathwaysStore).readDocument(any());

        linkedDoc = tracesDoc(pathwaysDoc.asDocRef());
        publisher = newPublisher();
    }

    @Test
    void aStoreWithNoPathwaysDocumentHandsNothingOver() throws IOException {
        publisher.pushArchive(tracesDoc(null), 0, stagedBatch("batch1", TRACE_A, NAME_A));

        assertThat(queueRoot()).doesNotExist();
        verify(documentStoreRegistry, never()).getDocumentStore(any());
    }

    @Test
    void eachTraceLandsOnTheShardItsOperationNameHashesTo() throws IOException {
        publisher.pushArchive(linkedDoc, 0, stagedBatch("batch1", TRACE_A, NAME_A, TRACE_B, NAME_B));

        final int shardA = ShardKeyRouter.computeShardIndex(NAME_A, SHARD_COUNT);
        final int shardB = ShardKeyRouter.computeShardIndex(NAME_B, SHARD_COUNT);
        assertThat(shardA)
                .as("the fixture only says something if the two names hash apart")
                .isNotEqualTo(shardB);

        assertThat(rootNamesIn(shardA)).containsExactly(NAME_A);
        assertThat(rootNamesIn(shardB)).containsExactly(NAME_B);
    }

    @Test
    void aTraceWhoseNameNeedsTheLookupTableIsStillHandedOver() throws IOException {
        publisher.pushArchive(linkedDoc, 0, stagedBatch("batch1", TRACE_A, NAME_LONG));

        assertThat(rootNamesIn(ShardKeyRouter.computeShardIndex(NAME_LONG, SHARD_COUNT)))
                .as("a long operation name must not exclude a trace from pathways")
                .containsExactly(NAME_LONG);
    }

    @Test
    void anItemCarriesTheSpansAndTheRoot() throws IOException {
        publisher.pushArchive(linkedDoc, 0, stagedBatch("batch1", TRACE_A, NAME_A));

        final Path item = itemsIn(ShardKeyRouter.computeShardIndex(NAME_A, SHARD_COUNT)).getFirst();
        try (final QueueItemReader reader = new QueueItemReader(item, BYTE_BUFFERS, BYTE_BUFFER_FACTORY)) {
            final List<TraceRoot> roots = new ArrayList<>();
            reader.forEachTrace((root, trace) -> {
                roots.add(root);
                assertThat(trace.getParentSpanIdMap()).as("the spans came too").isNotEmpty();
            });
            assertThat(roots).hasSize(1);
            assertThat(roots.getFirst().getTraceId()).isEqualTo(TRACE_A);
            assertThat(roots.getFirst().getName()).isEqualTo(NAME_A);
        }
    }

    /**
     * Hand-over is at-least-once, not exactly-once. More spans arriving for a trace re-stages it, and
     * it is then offered again rather than being checked against a record of what has gone before.
     *
     * <p>That is the deliberate trade. The alternative is a persistent per-trace marker on the
     * producer — the growing table this design set out to remove — and the consumer already discards a
     * trace it has applied, so a repeat costs some queue traffic and changes no model.
     */
    @Test
    void moreSpansForATraceOfferItAgain() throws IOException {
        publisher.pushArchive(linkedDoc, 0, stagedBatch("batch1", TRACE_A, NAME_A));
        final int shard = ShardKeyRouter.computeShardIndex(NAME_A, SHARD_COUNT);
        assertThat(itemsIn(shard)).hasSize(1);

        publisher.pushArchive(linkedDoc, 0, stagedBatch("batch2", TRACE_A, NAME_A));

        assertThat(itemsIn(shard))
                .as("offered again rather than silently dropped")
                .hasSize(2);
    }


    @Test
    void aFailedHandOverLeavesTheBucketUnpublished() throws IOException {
        // A file where the queue directory needs to be, so creating it fails.
        Files.createFile(pathwaysShared.resolve(PlanBConstants.QUEUE_DIR_NAME));

        assertThatThrownBy(() ->
                publisher.pushArchive(linkedDoc, 0, stagedBatch("batch1", TRACE_A, NAME_A)))
                .isInstanceOf(IOException.class);

        assertThat(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME))
                .as("the bucket must not publish, so the whole cycle is retried and nothing is lost")
                .doesNotExist();
    }

    @Test
    void pastTheDepthLimitTracesAreShedAndTheBucketStillPublishes() throws IOException {
        final int shard = ShardKeyRouter.computeShardIndex(NAME_A, SHARD_COUNT);
        final Path shardDir = Files.createDirectories(shardDir(shard));
        for (int i = 0; i <= TraceMergeCompletionStrategy.MAX_QUEUE_DEPTH_PER_SHARD; i++) {
            Files.createDirectory(shardDir.resolve(QueueItem.newName(i)));
        }
        final int before = itemsIn(shard).size();

        publisher.pushArchive(linkedDoc, 0, stagedBatch("batch1", TRACE_A, NAME_A));

        assertThat(itemsIn(shard))
                .as("nothing is added to a queue nobody is draining")
                .hasSize(before);
        assertThat(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME))
                .as("trace archiving must not be held hostage to the health of pathways")
                .exists();
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    private SharedFileStorePublisher newPublisher() {
        // pushArchive does not use NodeInfo, so null is fine here.
        return new SharedFileStorePublisher(
                null,
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                new PlanBPaths(tempDir.resolve("local_state")),
                Map.of(StateType.TRACE, new TraceMergeCompletionStrategy(
                        () -> documentStoreRegistry, BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                        new PlanBPaths(tempDir.resolve("local_state")))));
    }

    private TracesDoc tracesDoc(final DocRef pathwaysDocRef) {
        return TracesDoc.tracesBuilder()
                .uuid("11111111-2222-3333-4444-555555555555")
                .name("test-traces")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(1, traceShared.toString()))
                        .build())
                .pathwaysDocRef(pathwaysDocRef)
                .build();
    }

    // A staged batch produced the way publishing really produces one: spans written into a holding
    // shard, then publish stages them into a per-day delta.
    //
    // Building the delta by calling insert on it directly would not do, even though it is shorter.
    // insert writes a trace root, and staging deliberately does not — the bucket derives its own. A
    // delta carrying roots makes the bucket already hold one by the time mergeComplete runs, so the
    // hand-over condition never fires and every assertion here passes vacuously.
    private StagedArchive stagedBatch(final String dirName, final String... traceIdsAndNames)
            throws IOException {
        final Path holding = Files.createDirectories(tempDir.resolve(dirName + "_holding"));
        final Path deltaBase = Files.createDirectories(tempDir.resolve(dirName + "_delta"));
        try (final TraceDb db = TraceDb.create(
                holding, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, linkedDoc, false, false)) {
            db.write(writer -> {
                for (int i = 0; i < traceIdsAndNames.length; i += 2) {
                    db.insert(writer, new SpanKV(
                            SpanKey.builder()
                                    .traceId(traceIdsAndNames[i])
                                    .spanId(ROOT_SPAN)
                                    .parentSpanId("")
                                    .build(),
                            SpanValue.builder()
                                    .name(traceIdsAndNames[i + 1])
                                    .startTimeUnixNano(NanoTimeUtil.fromInstant(DAY))
                                    .endTimeUnixNano(NanoTimeUtil.fromInstant(DAY))
                                    .insertTime(NanoTimeUtil.fromInstant(DAY))
                                    .build()));
                }
            });
        }
        try (final TraceDb db = TraceDb.create(
                holding, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, linkedDoc, false, false)) {
            db.publish(DAY.plusSeconds(60), deltaBase);
        }
        final Path deltaDir = deltaBase.resolve(DAY_LABEL);
        assertThat(deltaDir).as("the spans staged into the expected day bucket").isDirectory();
        return new StagedArchive(DAY_LABEL, deltaDir);
    }

    private Path queueRoot() {
        return pathwaysShared
                .resolve(PlanBConstants.QUEUE_DIR_NAME)
                .resolve(pathwaysDoc.getUuid());
    }

    // Through ShardQueue, so this cannot drift from where the code under test writes.
    private Path shardDir(final int shardIndex) {
        return ShardQueue.of(pathwaysDoc.getSharedFileStore(), pathwaysDoc.getUuid(), shardIndex).dir();
    }

    private List<Path> itemsIn(final int shardIndex) throws IOException {
        final Path dir = shardDir(shardIndex);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream.filter(QueueItem::isItem).sorted(QueueItem.BY_ORDER_KEY).toList();
        }
    }

    private List<String> rootNamesIn(final int shardIndex) throws IOException {
        final List<String> names = new ArrayList<>();
        for (final Path item : itemsIn(shardIndex)) {
            try (final QueueItemReader reader =
                         new QueueItemReader(item, BYTE_BUFFERS, BYTE_BUFFER_FACTORY)) {
                reader.forEachTrace((root, trace) -> names.add(root.getName()));
            }
        }
        return names;
    }

    private Path bucketDir() {
        return traceShared
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(linkedDoc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(0))
                .resolve(DAY_LABEL);
    }
}
