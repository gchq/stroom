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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.shared.PageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a trace that has finished its journey — ingested, held, published into a time bucket —
 * is then picked up and turned into a pathway.
 *
 * <p>It drives the real entry point, {@code PathwaysProcessor.exec()}, so a trace that goes
 * undiscovered shows up here the way it would in the product: still listed in the trace screens,
 * contributing to no pathway, with nothing logged to say it was skipped.
 *
 * <p>Two traces are published together, differing only in how long their span names are. That is
 * not cosmetic. A name over 32 bytes is stored through the lookup table and a shorter one inline,
 * which sends the two spans down different routes when the bucket is merged — one decoded and
 * written through {@code insert}, one copied by a direct put. Both must still end up carrying the
 * {@code trace-roots-merge-time} entry pathways searches on, so both must yield a pathway.

 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestPathwaysDiscoversPublishedTraces {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final String NODE = "node1";
    private static final String TRACE_INLINE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TRACE_LOOKUP = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    private static final String SHORT_NAME = "GET /api";
    private static final String LONG_NAME =
            "GET /api/v1/customers/{id}/orders/{orderId}/shipments/{shipmentId}";

    @Mock
    private PathwaysStore pathwaysStore;
    @Mock
    private MessageReceiverFactory messageReceiverFactory;
    @Mock
    private MessageReceiver messageReceiver;
    @Mock
    private PathCreator pathCreator;
    @Mock
    private ShardManager shardManager;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private ClusterLockService clusterLockService;

    @Test
    void everyPublishedTraceBecomesAPathway(@TempDir final Path tempDir) throws Exception {
        final Path shared = Files.createDirectory(tempDir.resolve("shared"));
        final PlanBDoc tracesDoc = buildTracesDoc(shared);
        final Instant now = Instant.now();

        publishBothTraces(tempDir, shared, tracesDoc, now);

        final PathwaysDoc pathwaysDoc = buildPathwaysDoc(tracesDoc);
        final PathwaysProcessor processor = buildProcessor(tempDir, tracesDoc, pathwaysDoc, shared);

        processor.exec();

        assertThat(pathwayNames(processor, pathwaysDoc))
                .as("both published traces must contribute a pathway, whatever their span names")
                .hasSize(2);
    }

    // Writes both traces into a holding store, publishes them into one day bucket, and pushes that
    // bucket to the shared store — the route a trace really takes before pathways sees it.
    private void publishBothTraces(final Path tempDir,
                                   final Path shared,
                                   final PlanBDoc tracesDoc,
                                   final Instant now) throws IOException {
        final Path holding = Files.createDirectory(tempDir.resolve("holding"));
        final Path deltaBase = Files.createDirectory(tempDir.resolve("delta"));

        try (final TraceDb db = TraceDb.create(
                holding, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, tracesDoc, false, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_INLINE), span(SHORT_NAME, now)));
                db.insert(writer, new SpanKV(childKey(TRACE_INLINE), span(SHORT_NAME, now)));
                db.insert(writer, new SpanKV(rootKey(TRACE_LOOKUP), span(LONG_NAME, now)));
                db.insert(writer, new SpanKV(childKey(TRACE_LOOKUP), span(LONG_NAME, now)));
            });
        }
        // Publish everything: a cut-off after the spans selects both traces.
        try (final TraceDb db = TraceDb.create(
                holding, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, tracesDoc, false, false)) {
            db.publish(now.plusSeconds(60), deltaBase);
        }

        final String dayLabel = DateTimeFormatter.ISO_LOCAL_DATE.format(now.atZone(ZoneOffset.UTC));
        final Path deltaDir = deltaBase.resolve(dayLabel);
        assertThat(deltaDir).as("both traces staged into today's bucket").isDirectory();

        // pushArchive does not use NodeInfo, so null is fine here.
        new SharedFileStorePublisher(null, BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                new PlanBPaths(tempDir.resolve("local_state")))
                .pushArchive(tracesDoc, 0, new StagedArchive(dayLabel, deltaDir));

        assertThat(bucketDir(shared, tracesDoc, dayLabel).resolve(PlanBConstants.VERSION_FILE_NAME))
                .as("the bucket is published")
                .exists();
    }

    private PathwaysProcessor buildProcessor(final Path tempDir,
                                             final PlanBDoc tracesDoc,
                                             final PathwaysDoc pathwaysDoc,
                                             final Path shared) throws IOException {
        final Path pathwaysHome = Files.createDirectory(tempDir.resolve("pathways_home"));
        Mockito.when(pathCreator.toAppPath(Mockito.anyString())).thenReturn(pathwaysHome);

        Mockito.when(pathwaysStore.list()).thenReturn(List.of(pathwaysDoc.asDocRef()));
        Mockito.when(pathwaysStore.readDocument(Mockito.any())).thenReturn(pathwaysDoc);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE);

        Mockito.when(shardManager.isSnapshotNode()).thenReturn(false);
        Mockito.when(shardManager.getDoc(Mockito.anyString())).thenReturn(tracesDoc);

        // Serve the published bucket the way ShardManager would, by opening it read-only.
        Mockito.when(shardManager.getArchive(Mockito.any(), Mockito.anyInt(), Mockito.any(),
                        Mockito.<Function<stroom.planb.impl.dao.Db<?, ?>, Object>>any()))
                .thenAnswer(invocation -> {
                    final ArchiveShardRef ref = invocation.getArgument(2);
                    final Function<stroom.planb.impl.dao.Db<?, ?>, Object> function =
                            invocation.getArgument(3);
                    try (final TraceDb archive = TraceDb.create(
                            ref.dir(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, tracesDoc, true)) {
                        return function.apply(archive);
                    }
                });

        Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(clusterLockService).tryLock(Mockito.anyString(), Mockito.any(Runnable.class));

        Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, Consumer.class).accept(messageReceiver);
            return null;
        }).when(messageReceiverFactory).create(Mockito.anyString(), Mockito.any());

        return new PathwaysProcessor(
                pathwaysStore,
                messageReceiverFactory,
                pathCreator,
                BYTE_BUFFERS,
                new PathwaySerde(BYTE_BUFFER_FACTORY),
                shardManager,
                nodeInfo,
                clusterLockService,
                new ArchiveShardLocator());
    }

    private static List<String> pathwayNames(final PathwaysProcessor processor,
                                             final PathwaysDoc doc) {
        return processor.findPathways(new FindPathwayCriteria(
                        new PageRequest(0, 100), null, doc.asDocRef()))
                .getValues().stream()
                .map(Object::toString)
                .toList();
    }

    private static Path bucketDir(final Path shared, final PlanBDoc doc, final String dayLabel) {
        return shared
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(0))
                .resolve(dayLabel);
    }

    private static PathwaysDoc buildPathwaysDoc(final PlanBDoc tracesDoc) {
        return PathwaysDoc.builder()
                .uuid("22222222-2222-2222-2222-222222222222")
                .name("test-pathways")
                .tracesDocRef(tracesDoc.asDocRef())
                .infoFeed(DocRef.builder().type("Feed").uuid("feed-uuid").name("INFO_FEED").build())
                .processingNode(NODE)
                .build();
    }

    private static PlanBDoc buildTracesDoc(final Path sharedPath) {
        return PlanBDoc.builder()
                .uuid("11111111-1111-1111-1111-111111111111")
                .name("test-traces")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .sharedFileStore(new SharedFileStoreSettings(
                                1, sharedPath.toAbsolutePath().toString()))
                        .build())
                .build();
    }

    private static SpanKey rootKey(final String traceId) {
        return SpanKey.builder().traceId(traceId).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanKey childKey(final String traceId) {
        return SpanKey.builder().traceId(traceId).parentSpanId(ROOT_SPAN).spanId(CHILD_SPAN).build();
    }

    // Every span carries an attribute because NodeMutatorImpl reads the attribute list while
    // building a pathway and does not check it for null. The attribute strings are kept short so the
    // span name stays the only string crossing the lookup threshold, which is the variable here.
    private static SpanValue span(final String name, final Instant time) {
        return SpanValue.builder()
                .name(name)
                .attributes(List.of(KeyValue.builder()
                        .key("svc")
                        .value(AnyValue.stringValue("api"))
                        .build()))
                .startTimeUnixNano(NanoTimeUtil.fromInstant(time))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(time))
                .insertTime(NanoTimeUtil.fromInstant(time))
                .build();
    }
}
