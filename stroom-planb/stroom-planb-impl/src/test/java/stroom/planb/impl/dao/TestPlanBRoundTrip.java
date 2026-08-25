/*
 * Copyright 2025 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.dao.ShardWriters.ShardWriter;
import stroom.planb.impl.dao.histogram.HistogramDb;
import stroom.planb.impl.dao.metric.MetricDb;
import stroom.planb.impl.dao.rangestate.RangeStateDb;
import stroom.planb.impl.dao.rangestate.RangeStateRequest;
import stroom.planb.impl.dao.session.SessionDb;
import stroom.planb.impl.dao.session.SessionRequest;
import stroom.planb.impl.dao.state.StateDb;
import stroom.planb.impl.dao.temporalrangestate.TemporalRangeStateDb;
import stroom.planb.impl.dao.temporalrangestate.TemporalRangeStateRequest;
import stroom.planb.impl.dao.temporalstate.TemporalStateDb;
import stroom.planb.impl.dao.temporalstate.TemporalStateRequest;
import stroom.planb.impl.data.FileTransferClient;
import stroom.planb.impl.data.FileTransferClientImpl;
import stroom.planb.impl.data.MergeProcessor;
import stroom.planb.impl.data.PartDestination;
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.data.State;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.Tag;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.HistogramSettings;
import stroom.planb.shared.MetricSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeStateSettings;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TemporalRangeStateSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Joins up the whole Plan B write path against real components on disk, one store type at a time.
 * A pipeline stream writes rows through {@link ShardWriters}, the zip it produces is delivered by
 * the single node local branch of {@link FileTransferClientImpl}, {@link MergeProcessor} merges it
 * into the shard, and the values are read back through {@link ShardManager} the way a query reads
 * them.
 * <p>
 * The per store tests in this package write to an LMDB dir directly, so none of them would notice
 * a break in the steps between the writer and the shard. This test exists to notice.
 */
class TestPlanBRoundTrip {

    private static final Instant REF_TIME = Instant.parse("2000-01-01T00:00:00.000Z");
    private static final long MAX_STORE_SIZE = ByteSize.ofGibibytes(100).getBytes();

    @Test
    void stateRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("state_store", StateType.STATE, new StateSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addState(resolve(writer, doc), new State(
                        KeyPrefix.create("user1"),
                        ValString.create("london")));
            }
            node.merge();

            final String value = node.read(doc, db ->
                    string(((StateDb) db).get(KeyPrefix.create("user1"))));
            assertThat(value).isEqualTo("london");
        }
    }

    @Test
    void temporalStateRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("temporal_state_store", StateType.TEMPORAL_STATE,
                new TemporalStateSettings.Builder()
                        .maxStoreSize(MAX_STORE_SIZE)
                        .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addTemporalState(resolve(writer, doc), new TemporalState(
                        new TemporalKey(KeyPrefix.create("user1"), REF_TIME),
                        ValString.create("london")));
            }
            node.merge();

            // A temporal state is effective from its time onwards, so a later lookup finds it.
            final String value = node.read(doc, db -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        new TemporalKey(KeyPrefix.create("user1"), REF_TIME.plusSeconds(60)));
                final TemporalState state = ((TemporalStateDb) db).getState(request);
                return state == null
                        ? null
                        : state.val().toString();
            });
            assertThat(value).isEqualTo("london");
        }
    }

    @Test
    void rangeStateRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("range_state_store", StateType.RANGED_STATE,
                new RangeStateSettings.Builder()
                        .maxStoreSize(MAX_STORE_SIZE)
                        .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addRangeState(resolve(writer, doc), new RangeState(
                        new RangeState.Key(10, 30),
                        ValString.create("in range")));
            }
            node.merge();

            // A key inside the range resolves; one outside it does not.
            final String inRange = node.read(doc, db -> {
                final RangeState state = ((RangeStateDb) db).getState(new RangeStateRequest(20));
                return state == null
                        ? null
                        : state.val().toString();
            });
            assertThat(inRange).isEqualTo("in range");
            final RangeState outOfRange = node.read(doc, db ->
                    ((RangeStateDb) db).getState(new RangeStateRequest(31)));
            assertThat(outOfRange).isNull();
        }
    }

    @Test
    void temporalRangeStateRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("temporal_range_state_store", StateType.TEMPORAL_RANGED_STATE,
                new TemporalRangeStateSettings.Builder()
                        .maxStoreSize(MAX_STORE_SIZE)
                        .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addTemporalRangeState(resolve(writer, doc), new TemporalRangeState(
                        new TemporalRangeState.Key(10, 30, REF_TIME),
                        ValString.create("in range")));
            }
            node.merge();

            final String value = node.read(doc, db -> {
                final TemporalRangeStateRequest request =
                        new TemporalRangeStateRequest(20, REF_TIME.plusSeconds(60));
                final TemporalRangeState state = ((TemporalRangeStateDb) db).getState(request);
                return state == null
                        ? null
                        : state.val().toString();
            });
            assertThat(value).isEqualTo("in range");
        }
    }

    @Test
    void sessionRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("session_store", StateType.SESSION, new SessionSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());
        final Instant end = REF_TIME.plusSeconds(60);

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addSession(resolve(writer, doc), new Session(
                        KeyPrefix.create("user1"),
                        REF_TIME,
                        end));
            }
            node.merge();

            // A time inside the session resolves to the session that spans it.
            final String span = node.read(doc, db -> {
                final SessionRequest request =
                        new SessionRequest(KeyPrefix.create("user1"), REF_TIME.plusSeconds(30));
                final Session session = ((SessionDb) db).getState(request);
                return session == null
                        ? null
                        : session.getStart() + "/" + session.getEnd();
            });
            assertThat(span).isEqualTo(REF_TIME + "/" + end);
        }
    }

    @Test
    void histogramRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("histogram_store", StateType.HISTOGRAM, new HistogramSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                final PlanBDoc resolved = resolve(writer, doc);
                for (int i = 0; i < 3; i++) {
                    writer.addHistogramValue(resolved, new TemporalValue(tagsKey(), 1L));
                }
            }
            node.merge();

            // Counts for one key and time are summed as they are written.
            final Long count = node.read(doc, db -> ((HistogramDb) db).get(tagsKey()));
            assertThat(count).isEqualTo(3L);
        }
    }

    @Test
    void metricRoundTrip(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("metric_store", StateType.METRIC, new MetricSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                final PlanBDoc resolved = resolve(writer, doc);
                writer.addMetricValue(resolved, new TemporalValue(tagsKey(), 5L));
                writer.addMetricValue(resolved, new TemporalValue(tagsKey(), 7L));
            }
            node.merge();

            // The default metric value schema keeps the latest value for the key and time.
            final Long latest = node.read(doc, db -> ((MetricDb) db).get(tagsKey()));
            assertThat(latest).isEqualTo(7L);
        }
    }

    /**
     * One stream commonly writes to several Plan B stores. All of them must reach their own shard
     * from the single zip the stream produces, and the writer dir must be left empty afterwards.
     */
    @Test
    void oneStreamFeedsManyStores(@TempDir final Path rootDir) throws IOException {
        final PlanBDoc stateDoc = doc("state_store", StateType.STATE, new StateSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());
        final PlanBDoc temporalDoc = doc("temporal_state_store", StateType.TEMPORAL_STATE,
                new TemporalStateSettings.Builder()
                        .maxStoreSize(MAX_STORE_SIZE)
                        .build());

        try (final Node node = new Node(rootDir, List.of(stateDoc, temporalDoc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addState(resolve(writer, stateDoc), new State(
                        KeyPrefix.create("user1"),
                        ValString.create("london")));
                writer.addTemporalState(resolve(writer, temporalDoc), new TemporalState(
                        new TemporalKey(KeyPrefix.create("user2"), REF_TIME),
                        ValString.create("paris")));
            }
            node.merge();

            final String stateValue = node.read(stateDoc, db ->
                    string(((StateDb) db).get(KeyPrefix.create("user1"))));
            assertThat(stateValue).isEqualTo("london");
            final String temporalValue = node.read(temporalDoc, db -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        new TemporalKey(KeyPrefix.create("user2"), REF_TIME));
                final TemporalState state = ((TemporalStateDb) db).getState(request);
                return state == null
                        ? null
                        : state.val().toString();
            });
            assertThat(temporalValue).isEqualTo("paris");

            assertThat(node.writerDirContents()).isEmpty();
        }
    }

    /**
     * Shards are long lived, so a second stream merges into a shard that already holds data. State
     * stores overwrite by default, so the later value must win.
     */
    @Test
    void laterStreamOverwritesState(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("state_store", StateType.STATE, new StateSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            try (final ShardWriter writer = node.createWriter()) {
                writer.addState(resolve(writer, doc), new State(
                        KeyPrefix.create("user1"),
                        ValString.create("london")));
            }
            node.merge();

            try (final ShardWriter writer = node.createWriter()) {
                writer.addState(resolve(writer, doc), new State(
                        KeyPrefix.create("user1"),
                        ValString.create("paris")));
            }
            node.merge();

            final String value = node.read(doc, db ->
                    string(((StateDb) db).get(KeyPrefix.create("user1"))));
            assertThat(value).isEqualTo("paris");
        }
    }

    /**
     * Histogram merges add to the count already in the shard rather than replacing it, so two
     * streams each contributing one event leave a count of two.
     */
    @Test
    void histogramCountsAddAcrossStreams(@TempDir final Path rootDir) {
        final PlanBDoc doc = doc("histogram_store", StateType.HISTOGRAM, new HistogramSettings.Builder()
                .maxStoreSize(MAX_STORE_SIZE)
                .build());

        try (final Node node = new Node(rootDir, List.of(doc))) {
            for (int stream = 0; stream < 2; stream++) {
                try (final ShardWriter writer = node.createWriter()) {
                    writer.addHistogramValue(resolve(writer, doc), new TemporalValue(tagsKey(), 1L));
                }
                node.merge();
            }

            final Long count = node.read(doc, db -> ((HistogramDb) db).get(tagsKey()));
            assertThat(count).isEqualTo(2L);
        }
    }

    private PlanBDoc resolve(final ShardWriter writer, final PlanBDoc doc) {
        final Optional<PlanBDoc> resolved = writer.getDoc(doc.getName(), error -> {
            throw new AssertionError("Could not resolve map '" + doc.getName() + "': " + error);
        });
        return resolved.orElseThrow(() ->
                new AssertionError("No doc found for map '" + doc.getName() + "'"));
    }

    private PlanBDoc doc(final String name,
                         final StateType stateType,
                         final AbstractPlanBSettings settings) {
        return PlanBDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .stateType(stateType)
                .settings(settings)
                .build();
    }

    private TemporalKey tagsKey() {
        return new TemporalKey(KeyPrefix.create(List.of(
                new Tag("host", ValString.create("server1")),
                new Tag("app", ValString.create("stroom")))), REF_TIME);
    }

    private String string(final Val val) {
        return val == null
                ? null
                : val.toString();
    }

    /**
     * A single Plan B node wired from the real classes: a writer that publishes to itself over the
     * local delivery branch, a merge processor and the shard manager that reads share the same
     * directory tree.
     */
    private static final class Node implements AutoCloseable {

        private final StatePaths statePaths;
        private final ShardManager shardManager;
        private final MergeProcessor mergeProcessor;
        private final ShardWriters shardWriters;
        private final AtomicLong metaId = new AtomicLong();

        private Node(final Path rootDir, final List<PlanBDoc> docs) {
            final Map<String, PlanBDoc> byName = docs.stream()
                    .collect(Collectors.toMap(PlanBDoc::getName, Function.identity()));
            final Map<String, PlanBDoc> byUuid = docs.stream()
                    .collect(Collectors.toMap(PlanBDoc::getUuid, Function.identity()));

            final PlanBDocCache docCache = Mockito.mock(PlanBDocCache.class);
            Mockito.when(docCache.get(Mockito.anyString()))
                    .thenAnswer(invocation -> byName.get(invocation.<String>getArgument(0)));
            final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
            Mockito.when(docStore.readDocument(Mockito.any(DocRef.class)))
                    .thenAnswer(invocation -> byUuid.get(invocation.<DocRef>getArgument(0).getUuid()));

            // Everything runs on the calling thread so that a merge has finished by the time the
            // test reads.
            final ExecutorProvider executorProvider = new ExecutorProvider() {
                @Override
                public Executor get() {
                    return Runnable::run;
                }

                @Override
                public Executor get(final ThreadPool threadPool) {
                    return Runnable::run;
                }
            };

            // No node list configured, so the client treats this as a single node install and
            // delivers the part to itself rather than over HTTP.
            final NodeInfo nodeInfo = () -> "node1";
            final PlanBConfig config = new PlanBConfig(rootDir.toAbsolutePath().toString());
            final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
            final ByteBuffers byteBuffers = new ByteBuffers(byteBufferFactory);

            statePaths = new StatePaths(rootDir);
            shardManager = new ShardManager(
                    byteBuffers,
                    byteBufferFactory,
                    docCache,
                    docStore,
                    nodeInfo,
                    () -> config,
                    statePaths,
                    null,
                    new SimpleTaskContextFactory(),
                    executorProvider);
            final MergeProcessor processor = new MergeProcessor(
                    statePaths,
                    new MockSecurityContext(),
                    new SimpleTaskContextFactory(),
                    shardManager,
                    executorProvider,
                    () -> config);
            mergeProcessor = processor;
            final PartDestination partDestination = new PartDestination(
                    new MockSecurityContext(),
                    statePaths,
                    () -> processor);
            final FileTransferClient fileTransferClient = new FileTransferClientImpl(
                    () -> config,
                    null,
                    nodeInfo,
                    null,
                    null,
                    partDestination,
                    new MockSecurityContext(),
                    executorProvider);
            shardWriters = new ShardWriters(
                    docCache,
                    byteBuffers,
                    byteBufferFactory,
                    statePaths,
                    fileTransferClient);
        }

        private ShardWriter createWriter() {
            return shardWriters.createWriter(Meta.builder().id(metaId.incrementAndGet()).build());
        }

        private void merge() {
            mergeProcessor.mergeCurrent();
        }

        private <R> R read(final PlanBDoc doc, final Function<Db<?, ?>, R> function) {
            return shardManager.get(doc.getName(), function);
        }

        private List<String> writerDirContents() throws IOException {
            if (!Files.isDirectory(statePaths.getWriterDir())) {
                return List.of();
            }
            try (final Stream<Path> stream = Files.list(statePaths.getWriterDir())) {
                return stream.map(path -> path.getFileName().toString()).toList();
            }
        }

        @Override
        public void close() {
            // The shard envs must be closed before JUnit deletes the @TempDir.
            shardManager.closeAll();
        }
    }
}
