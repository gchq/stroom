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

package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PathwaysProcessor} finds traces to process by scanning a published bucket's
 * {@code trace-roots-merge-time} index and taking entries older than its grace period. A trace that
 * reaches a bucket without an entry there is therefore invisible to pathways, however complete the
 * bucket's spans and roots are.
 *
 * <p>These tests check the index survives the route a trace actually takes — inserted into a holding
 * shard, published into a delta, pushed into a bucket — and check it the same way for both of the
 * routes a span can take through that last merge. A span whose stored bytes reference the lookup
 * table is decoded and written through {@code insert}, which stamps the entry; a span that needs no
 * lookup is merged by a direct put, which does not. The entry has to end up there either way, which
 * is why the two are asserted separately rather than one standing in for the other.
 */
class TestBucketMergeTimeIndex {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final Instant CUTOFF = Instant.parse("2024-02-01T00:00:00.000Z");
    private static final Instant ROOT_TIME = Instant.parse("2024-01-10T09:00:00.000Z");
    private static final String DAY_LABEL = "2024-01-10";

    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    // The names differ in length on purpose: LookupSerdeImpl stores a string of 32 bytes or fewer
    // inline and sends anything longer through the UID lookup table, so these two pick one merge
    // route each. If that threshold moves, adjust these rather than assuming both routes are tested.
    private static final String SHORT_NAME = "GET /api";
    private static final String LONG_NAME =
            "GET /api/v1/customers/{id}/orders/{orderId}/shipments/{shipmentId}";

    @Test
    void holdingShardIndexesTheRootOnInsert(@TempDir final Path tempDir) throws IOException {
        final Path holdingDir = Files.createDirectory(tempDir.resolve("holding"));
        final PlanBDoc doc = buildDoc();

        insertTrace(holdingDir, doc, SHORT_NAME);

        try (final TraceDb db = openHolding(holdingDir, doc)) {
            assertThat(mergeTimeTraceIds(db))
                    .as("insert stamps a merge time for the root span")
                    .containsExactly(TRACE_A);
        }
    }

    @Test
    void publishedBucketIndexesTheRootWhenSpanStringsAreStoredInline(@TempDir final Path tempDir)
            throws IOException {
        final Path bucket = publishAndPush(tempDir, SHORT_NAME);

        try (final TraceDb archive = TraceDb.create(bucket, BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                buildSharedDoc(tempDir.resolve("shared")), true)) {
            assertThat(spanCount(archive))
                    .as("the bucket holds the trace's spans")
                    .isEqualTo(2);
            assertThat(mergeTimeTraceIds(archive))
                    .as("pathways can only find a trace it has a merge time for")
                    .containsExactly(TRACE_A);
        }
    }

    @Test
    void publishedBucketIndexesTheRootWhenSpanStringsUseTheLookupTable(@TempDir final Path tempDir)
            throws IOException {
        final Path bucket = publishAndPush(tempDir, LONG_NAME);

        try (final TraceDb archive = TraceDb.create(bucket, BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                buildSharedDoc(tempDir.resolve("shared")), true)) {
            assertThat(spanCount(archive))
                    .as("the bucket holds the trace's spans")
                    .isEqualTo(2);
            assertThat(mergeTimeTraceIds(archive))
                    .as("pathways can only find a trace it has a merge time for")
                    .containsExactly(TRACE_A);
        }
    }

    // Runs the real route: insert into a holding shard, publish to a delta, push the delta into the
    // bucket for its day. Returns the bucket directory on the shared store.
    private static Path publishAndPush(final Path tempDir, final String spanName) throws IOException {
        final Path shared = Files.createDirectory(tempDir.resolve("shared"));
        final Path holdingDir = Files.createDirectory(tempDir.resolve("holding"));
        final Path deltaBase = Files.createDirectory(tempDir.resolve("delta"));
        final PlanBDoc doc = buildSharedDoc(shared);

        insertTrace(holdingDir, doc, spanName);

        try (final TraceDb db = openHolding(holdingDir, doc)) {
            // Spans plus the root-side rows of the retired root, so at least the two spans.
            assertThat(db.publish(CUTOFF, deltaBase))
                    .as("the trace's rows leave the holding shard")
                    .isGreaterThanOrEqualTo(2L);
        }

        final Path deltaDir = deltaBase.resolve(DAY_LABEL);
        assertThat(deltaDir).as("one bucket, the root's start day").isDirectory();

        // pushArchive does not use NodeInfo, so null is fine here.
        new SharedFileStorePublisher(null, BYTE_BUFFERS, BYTE_BUFFER_FACTORY,
                new PlanBPaths(tempDir.resolve("local_state")))
                .pushArchive(doc, 0, new StagedArchive(DAY_LABEL, deltaDir));

        return shared
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(0))
                .resolve(DAY_LABEL);
    }

    private static void insertTrace(final Path dir, final PlanBDoc doc, final String spanName) {
        try (final TraceDb db = openHolding(dir, doc)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(spanName)));
                db.insert(writer, new SpanKV(childKey(), span(spanName)));
            });
        }
    }

    // A holding shard is opened without its sort indexes, as HoldingShard does.
    private static TraceDb openHolding(final Path dir, final PlanBDoc doc) {
        return TraceDb.create(dir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false, false);
    }

    private static List<String> mergeTimeTraceIds(final TraceDb db) {
        final List<String> ids = new ArrayList<>();
        db.iterateRootsMergedBefore(Long.MAX_VALUE, id -> ids.add(HexStringUtil.encode(id)));
        return ids;
    }

    private static int spanCount(final TraceDb db) {
        return db.getTrace(HexStringUtil.decode(TRACE_A)).getParentSpanIdMap().values().stream()
                .mapToInt(List::size)
                .sum();
    }

    private static SpanKey rootKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanKey childKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId(ROOT_SPAN).spanId(CHILD_SPAN).build();
    }

    private static SpanValue span(final String name) {
        return SpanValue.builder()
                .name(name)
                .startTimeUnixNano(NanoTimeUtil.fromInstant(ROOT_TIME))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(ROOT_TIME))
                .insertTime(NanoTimeUtil.fromInstant(ROOT_TIME))
                .build();
    }

    private static PlanBDoc buildSharedDoc(final Path sharedPath) {
        return PlanBDoc.builder()
                .uuid("11111111-1111-1111-1111-111111111111")
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .sharedFileStore(new SharedFileStoreSettings(
                                1, sharedPath.toAbsolutePath().toString()))
                        .build())
                .build();
    }

    private static PlanBDoc buildDoc() {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .build())
                .build();
    }
}
