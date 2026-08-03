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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.shared.PageRequest;
import stroom.util.shared.time.SimpleDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@code TraceDb.evictArchivedRoots}, which reclaims the holding area once a trace can no longer
 * gain late spans.
 *
 * <p>Eviction moves nothing — {@code archiveRootedSpans} has already put the trace in its bucket, and the
 * bucket derives its own root — so this only has to decide <em>when</em> a root is safe to drop:
 * <ul>
 *   <li>past the cut-off, judged on the root's own end time so a leaky trace is still bounded;</li>
 *   <li>and with no non-root spans left here, because unarchived children mean archival has not caught
 *       up and evicting the root would strand them as orphans.</li>
 * </ul>
 */
class TestEvictArchivedRoots {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    private static final Instant ROOT_START = Instant.parse("2024-01-10T12:00:00.000Z");
    /** Later than the root's end, so anything aging on the root's own end is past it. */
    private static final Instant CUT_OFF = Instant.parse("2024-01-10T12:05:00.000Z");
    private static final Instant BEFORE_ROOT_START = Instant.parse("2024-01-10T11:00:00.000Z");

    @Test
    void evictsARootWhoseSpansHaveBeenArchived(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START)));
            });
            db.mergeComplete();
            // Children away to the bucket; root span and root entry stay behind.
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir, null);
            assertThat(traceIds(db)).containsExactly(TRACE_A);

            assertThat(db.evictArchivedRoots(CUT_OFF)).isGreaterThan(0);

            // Nothing left of the trace in the holding area.
            assertThat(traceIds(db)).isEmpty();
            assertThat(db.count()).isEqualTo(0);
            assertThat(db.rootSpan(HexStringUtil.decode(TRACE_A))).isEmpty();
        }
    }

    @Test
    void keepsARootThatIsNotYetPastTheCutOff(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START)));
            });
            db.mergeComplete();
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir, null);

            assertThat(db.evictArchivedRoots(BEFORE_ROOT_START)).isEqualTo(0);
            assertThat(traceIds(db)).containsExactly(TRACE_A);
        }
    }

    /**
     * The safety interlock: a root past the cut-off whose children are still here has not been archived
     * yet. Evicting it would leave the children rootless, so they would age out to a bucket chosen by
     * their own insert time instead of the trace's.
     */
    @Test
    void keepsARootWhoseChildrenAreNotYetArchived(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START)));
            });
            db.mergeComplete();

            // Note: no archiveRootedSpans call, so the child is still here.
            assertThat(db.evictArchivedRoots(CUT_OFF)).isEqualTo(0);
            assertThat(traceIds(db)).containsExactly(TRACE_A);
            assertThat(db.count()).isEqualTo(2);
        }
    }

    /**
     * An orphan trace has no root span, so nothing has archived it and no bucket can be derived for it.
     * It is left for the insert-time sweep rather than evicted here.
     */
    @Test
    void leavesOrphanTracesAlone(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(childKey(), span(ROOT_START))));
            db.mergeComplete();

            assertThat(db.evictArchivedRoots(CUT_OFF)).isEqualTo(0);
            assertThat(db.count()).isEqualTo(1);
        }
    }

    /** A root-only trace is archived via the merged-since gate, then becomes evictable. */
    @Test
    void evictsARootOnlyTraceOnceArchived(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(rootKey(), span(ROOT_START))));
            db.mergeComplete();
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir, BEFORE_ROOT_START);

            assertThat(db.evictArchivedRoots(CUT_OFF)).isGreaterThan(0);
            assertThat(traceIds(db)).isEmpty();
            assertThat(db.count()).isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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

    private static SpanKey rootKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanKey childKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId(ROOT_SPAN).spanId(CHILD_SPAN).build();
    }

    private static SpanValue span(final Instant start) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(start))
                .build();
    }

    private static List<String> traceIds(final TraceDb db) {
        final TracesResultPage page = db.findTraces(new FindTraceCriteria(
                new PageRequest(0, 1000), null, null, SimpleDuration.ZERO));
        return page.getValues().stream().map(TraceRoot::getTraceId).sorted().toList();
    }
}
