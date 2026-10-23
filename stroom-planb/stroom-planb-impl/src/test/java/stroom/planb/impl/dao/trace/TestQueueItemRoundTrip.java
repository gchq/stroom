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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Whether a queue item carries a trace out of a bucket and back intact.
 *
 * <p>The strings are the point. A span value does not hold its own text: anything up to 32 bytes is
 * written inline, anything longer goes through the UID lookup table, and anything over the LMDB key
 * limit through the hash lookup table. Only the first of those survives a copy that forgets the
 * lookup tables — and it survives silently, because the span bytes are copied verbatim either way.
 * So the fixture puts one trace in each band and the assertions compare resolved strings, not bytes.
 * A test built from short names alone would pass with the cloning removed entirely.
 */
class TestQueueItemRoundTrip {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    // The trace store the items below say they came from, which a consumer reads back to attribute
    // whatever it learns from them.
    private static final String SOURCE = "a-traces-doc-uuid";

    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    /** Stored inline in the span value; touches no lookup table. */
    private static final String INLINE_NAME = "GET /api";
    /** Over 32 bytes, so it goes through the UID lookup table. */
    private static final String UID_NAME =
            "GET /api/v1/customers/{id}/orders/{orderId}/shipments/{shipmentId}";
    /** Over the LMDB key limit, so it goes through the hash lookup table. */
    private static final String HASH_NAME = "SELECT " + "x".repeat(Db.MAX_KEY_LENGTH);

    private static final String TRACE_INLINE = "a".repeat(32);
    private static final String TRACE_UID = "b".repeat(32);
    private static final String TRACE_HASH = "c".repeat(32);

    private static final Map<String, String> NAME_BY_TRACE = new LinkedHashMap<>(Map.of(
            TRACE_INLINE, INLINE_NAME,
            TRACE_UID, UID_NAME,
            TRACE_HASH, HASH_NAME));

    @TempDir
    Path tempDir;

    private PlanBDoc doc;
    private Path bucketDir;
    private Path queueDir;
    private QueueItemWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-traces")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder().build())
                .build();
        bucketDir = Files.createDirectories(tempDir.resolve("bucket"));
        queueDir = Files.createDirectories(tempDir.resolve("queue"));
        writer = new QueueItemWriter(BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                Files.createDirectories(tempDir.resolve("local_build")));
        buildBucket();
    }

    @Test
    void anItemSaysWhichStoreItsTracesCameFrom() throws Exception {
        final Path itemDir = writeItem(allTraceIds()).orElseThrow();
        try (final QueueItemReader reader = openItem(itemDir)) {
            assertThat(reader.getSource())
                    .as("read back so what is learnt from these traces can say where they came from")
                    .isEqualTo(SOURCE);
        }
    }

    @Test
    void noLmdbEnvironmentIsOpenedWhereTheItemIsPublished() throws IOException {
        // The item is built locally and only its data file is copied up, because no LMDB environment
        // is ever opened on the shared mount. A lock.mdb anywhere under the queue means one was.
        writeItem(allTraceIds()).orElseThrow();

        try (final Stream<Path> files = Files.walk(queueDir)) {
            assertThat(files
                    .filter(f -> PlanBConstants.LOCK_FILE_NAME.equals(f.getFileName().toString()))
                    .toList())
                    .as("no lock file may reach the queue")
                    .isEmpty();
        }
    }

    @Test
    void theLocalBuildDirectoryIsNotLeftBehind() throws IOException {
        writeItem(allTraceIds()).orElseThrow();

        try (final Stream<Path> left = Files.list(tempDir.resolve("local_build"))) {
            assertThat(left.toList()).as("each item is removed once it has been copied up").isEmpty();
        }
    }

    @Test
    void everyTraceComesBackWithItsStringsIntact() throws IOException {
        final Map<String, Trace> expected = readAllFromBucket();

        final Path itemDir = writeItem(allTraceIds()).orElseThrow();

        final Map<String, Trace> actual = readAllFromItem(itemDir);
        assertThat(actual.keySet())
                .as("every trace handed over comes back")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (final Map.Entry<String, Trace> entry : expected.entrySet()) {
            assertThat(spanNames(actual.get(entry.getKey())))
                    .as("resolved span names for trace %s", entry.getKey())
                    .isEqualTo(spanNames(entry.getValue()));
            assertThat(attributeValues(actual.get(entry.getKey())))
                    .as("resolved attribute values for trace %s", entry.getKey())
                    .isEqualTo(attributeValues(entry.getValue()));
        }
    }

    @Test
    void aNameTooLongToStoreInlineStillComesBack() throws IOException {
        final Path itemDir = writeItem(allTraceIds()).orElseThrow();
        final Map<String, Trace> actual = readAllFromItem(itemDir);

        // Named individually so a failure says which storage band broke rather than just "a string".
        assertThat(spanNames(actual.get(TRACE_INLINE))).containsOnly(INLINE_NAME);
        assertThat(spanNames(actual.get(TRACE_UID))).containsOnly(UID_NAME);
        assertThat(spanNames(actual.get(TRACE_HASH))).containsOnly(HASH_NAME);
    }

    @Test
    void theStoredRootsComeWithTheTraces() throws IOException {
        final Path itemDir = writeItem(allTraceIds()).orElseThrow();

        final Map<String, TraceRoot> roots = new LinkedHashMap<>();
        try (final QueueItemReader reader = openItem(itemDir)) {
            reader.forEachTrace((root, trace) -> roots.put(root.getTraceId(), root));
        }

        assertThat(roots.keySet()).containsExactlyInAnyOrderElementsOf(NAME_BY_TRACE.keySet());
        NAME_BY_TRACE.forEach((traceId, name) -> {
            final TraceRoot root = roots.get(traceId);
            assertThat(root.getName()).as("root name for %s", traceId).isEqualTo(name);
            assertThat(root.isOrphan()).as("root for %s is a real root", traceId).isFalse();
            assertThat(root.getTotalSpans()).as("span count for %s", traceId).isEqualTo(2);
        });
    }

    @Test
    void onlyTheNamedTracesAreCopied() throws IOException {
        final Path itemDir = writeItem(List.of(idBytes(TRACE_UID))).orElseThrow();

        try (final QueueItemReader reader = openItem(itemDir)) {
            assertThat(reader.getTraceCount()).isEqualTo(1);
        }
        assertThat(readAllFromItem(itemDir).keySet()).containsExactly(TRACE_UID);
    }

    @Test
    void nothingIsWrittenWhenThereIsNothingToSend() throws IOException {
        assertThat(writeItem(List.of())).isEmpty();
        assertThat(queueDir).isEmptyDirectory();
    }

    @Test
    void theOrderKeyIsBothInTheNameAndInTheItem() throws IOException {
        final long orderKey = 1_700_000_000_123L;
        final Path itemDir = writer.write(openBucket(), allTraceIds(), queueDir, orderKey, SOURCE).orElseThrow();

        assertThat(QueueItem.orderKeyOf(itemDir))
                .as("readable from the name, so queue age costs no open")
                .isEqualTo(orderKey);
        try (final QueueItemReader reader = openItem(itemDir)) {
            assertThat(reader.getOrderKey()).isEqualTo(orderKey);
        }
    }

    @Test
    void itemsSortOldestFirst() throws IOException {
        final Path second = writer.write(openBucket(), allTraceIds(), queueDir, 2_000L, SOURCE).orElseThrow();
        final Path first = writer.write(openBucket(), allTraceIds(), queueDir, 1_000L, SOURCE).orElseThrow();
        final Path third = writer.write(openBucket(), allTraceIds(), queueDir, 3_000L, SOURCE).orElseThrow();

        final List<Path> sorted = new ArrayList<>(List.of(second, third, first));
        sorted.sort(QueueItem.BY_ORDER_KEY);

        assertThat(sorted).containsExactly(first, second, third);
    }

    @Test
    void aPartWrittenItemIsNotOfferedToAConsumer() throws IOException {
        final Path tmp = Files.createDirectory(queueDir.resolve(
                QueueItem.tmpName(QueueItem.newName(1_000L))));

        assertThat(QueueItem.isItem(tmp))
                .as("a temporary directory is never picked up")
                .isFalse();
        assertThat(QueueItem.isItem(writeItem(allTraceIds()).orElseThrow()))
                .as("a renamed one is")
                .isTrue();
    }

    @Test
    void anItemFromAnIncompatibleProducerIsRefused() throws IOException {
        final Path itemDir = writeItem(allTraceIds()).orElseThrow();
        overwriteFormatVersion(itemDir, QueueItem.FORMAT_VERSION + 1);

        assertThatThrownBy(() -> openItem(itemDir).close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("format version");
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    // A bucket as the trace store really produces one: spans written into a part, merged in, then
    // mergeComplete to derive the roots. Building the roots by hand would prove nothing about whether
    // a real bucket's rows copy.
    private void buildBucket() throws IOException {
        final Instant now = Instant.now();
        final Path partDir = Files.createDirectories(tempDir.resolve("part"));
        try (final TraceDb part = TraceDb.create(
                partDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false, false)) {
            part.write(writer -> NAME_BY_TRACE.forEach((traceId, name) -> {
                part.insert(writer, new SpanKV(key(traceId, ROOT_SPAN, ""), span(name, now)));
                part.insert(writer, new SpanKV(key(traceId, CHILD_SPAN, ROOT_SPAN), span(name, now)));
            }));
        }
        try (final TraceDb bucket = openBucket()) {
            bucket.merge(partDir);
            bucket.mergeComplete();
        }
    }

    private TraceDb openBucket() {
        return TraceDb.create(bucketDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false);
    }

    private Optional<Path> writeItem(final List<byte[]> traceIds) throws IOException {
        try (final TraceDb bucket = openBucket()) {
            return writer.write(bucket, traceIds, queueDir, System.currentTimeMillis(), SOURCE);
        }
    }

    private QueueItemReader openItem(final Path itemDir) {
        return new QueueItemReader(itemDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY);
    }

    private Map<String, Trace> readAllFromBucket() {
        final Map<String, Trace> traces = new LinkedHashMap<>();
        try (final TraceDb bucket = openBucket()) {
            NAME_BY_TRACE.keySet().forEach(traceId ->
                    bucket.findTrace(idBytes(traceId)).ifPresent(trace -> traces.put(traceId, trace)));
        }
        assertThat(traces).as("the fixture bucket holds every trace").hasSize(NAME_BY_TRACE.size());
        return traces;
    }

    private Map<String, Trace> readAllFromItem(final Path itemDir) {
        final Map<String, Trace> traces = new LinkedHashMap<>();
        try (final QueueItemReader reader = openItem(itemDir)) {
            reader.forEachTrace((root, trace) -> traces.put(root.getTraceId(), trace));
        }
        return traces;
    }

    private static List<byte[]> allTraceIds() {
        return NAME_BY_TRACE.keySet().stream().map(TestQueueItemRoundTrip::idBytes).toList();
    }

    private static byte[] idBytes(final String traceId) {
        return HexStringUtil.decode(traceId);
    }

    private static List<String> spanNames(final Trace trace) {
        return spans(trace).stream().map(Span::getName).sorted().toList();
    }

    private static List<String> attributeValues(final Trace trace) {
        return spans(trace).stream()
                .flatMap(span -> span.getAttributes().stream())
                .map(kv -> kv.getValue().getStringValue())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** Every span of the trace, whatever its place in the tree. */
    private static List<Span> spans(final Trace trace) {
        return trace.getParentSpanIdMap().values().stream().flatMap(List::stream).toList();
    }

    private static SpanKey key(final String traceId, final String spanId, final String parentSpanId) {
        return SpanKey.builder().traceId(traceId).spanId(spanId).parentSpanId(parentSpanId).build();
    }

    // The attribute carries the same string as the name, so a span exercises its storage band twice —
    // once through the span name and once through an attribute value, which are encoded separately.
    private static SpanValue span(final String name, final Instant time) {
        return SpanValue.builder()
                .name(name)
                .attributes(List.of(KeyValue.builder()
                        .key("http.route")
                        .value(AnyValue.stringValue(name))
                        .build()))
                .startTimeUnixNano(NanoTimeUtil.fromInstant(time))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(time))
                .insertTime(NanoTimeUtil.fromInstant(time))
                .build();
    }

    // Writes a format version the reader does not expect, standing in for an item left behind by a
    // producer of another vintage. It puts the entry directly rather than going through
    // QueueItemWriter, which only ever writes the current version — so the table name and key are
    // spelled out again here on purpose. Renaming either in QueueItem should fail this.
    private static void overwriteFormatVersion(final Path itemDir, final int version) {
        try (final TraceDb item = TraceDb.create(
                itemDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, QueueItem.doc(), false,
                QueueItem.HAS_SECONDARY_INDEXES)) {
            final Dbi<ByteBuffer> dbi = item.getEnv().openDbi("queue-item-info", DbiFlags.MDB_CREATE);
            item.getEnv().write(writer -> {
                final ByteBuffer key = ByteBuffer.allocateDirect(1);
                key.put((byte) 0).flip();
                final ByteBuffer value = ByteBuffer.allocateDirect(Long.BYTES);
                value.putLong(version).flip();
                dbi.put(writer.getWriteTxn(), key, value);
                writer.commit();
            });
        }
    }
}
