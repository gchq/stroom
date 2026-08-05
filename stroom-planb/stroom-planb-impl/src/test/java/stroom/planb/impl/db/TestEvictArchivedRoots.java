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
 *   <li>past the cut-off, judged on the root's own end time;</li>
 *   <li>with no non-root spans left here, because unarchived children mean archival has not caught up and
 *       evicting the root would strand them as orphans;</li>
 *   <li>and either gone quiet, or past the backstop cut-off. Quiet alone would pin a forever-active
 *       trace's root here indefinitely; the backstop alone would orphan traces with ordinary trailing
 *       activity.</li>
 * </ul>
 *
 * <p>Getting either of the last two wrong strands spans as orphans, which nothing archives or evicts — so
 * each has a test here that fails without its guard.
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
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);
            assertThat(traceIds(db)).containsExactly(TRACE_A);

            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isGreaterThan(0);

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
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);

            assertThat(db.evictArchivedRoots(BEFORE_ROOT_START, BEFORE_ROOT_START)).isEqualTo(0);
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
            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isEqualTo(0);
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

            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isEqualTo(0);
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
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);

            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isGreaterThan(0);
            assertThat(traceIds(db)).isEmpty();
            assertThat(db.count()).isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // Regressions: eviction must not manufacture orphans or unlatch the span cap
    // -----------------------------------------------------------------------

    /**
     * A trace whose root finished long ago but which is still emitting spans must not be evicted. Its
     * children are drained by every archival cycle, so the "no non-root spans present" guard is satisfied
     * between cycles — without a quiet check the root would be evicted out from under a live trace, the
     * next span would synthesize an orphan, and the whole trace would be pinned in the holding area
     * where nothing archives or evicts it.
     */
    @Test
    void doesNotEvictARootWhoseTraceIsStillActive(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();
        final Instant recentActivity = Instant.parse("2024-01-10T12:30:00.000Z");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START)));
                // A late child: same trace, but inserted well after the root finished.
                db.insert(writer, new SpanKV(childKey(),
                        spanWithInsert(ROOT_START, recentActivity)));
            });
            db.mergeComplete();
            // Children drained, so only the quiet check can save this root.
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);

            // Root's own end (12:00) is past this cut-off, but activity (12:30) is not.
            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isEqualTo(0);
            assertThat(traceIds(db)).containsExactly(TRACE_A);

            // Once activity is also past the cut-off it goes.
            assertThat(db.evictArchivedRoots(Instant.parse("2024-01-10T12:35:00.000Z"), BEFORE_ROOT_START))
                    .isGreaterThan(0);
            assertThat(traceIds(db)).isEmpty();
        }
    }

    /**
     * A trace that keeps emitting forever must still be evicted eventually. Waiting for quiet alone would
     * pin its root here indefinitely — and because it always has children, {@code archiveRootedSpans}
     * would keep re-pushing its ever-older start-time bucket every cycle, so no bucket would ever settle.
     * The per-trace span cap does not save this case: a trace trickling spans below the cap never reaches
     * it. The backstop cut-off bounds it regardless of activity.
     */
    @Test
    void evictsANeverQuietTraceAtTheBackstop(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();
        // Activity far in the future relative to every cut-off below: this trace is never quiet.
        final Instant stillActive = Instant.parse("2024-06-01T00:00:00.000Z");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), spanWithInsert(ROOT_START, stillActive)));
            });
            db.mergeComplete();
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);

            // Normal cut-off passed but the trace is still active, and the backstop is not yet reached.
            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isEqualTo(0);
            assertThat(traceIds(db)).containsExactly(TRACE_A);

            // Backstop now past the root's own end: evicted despite never having gone quiet.
            final Instant backstop = Instant.parse("2024-01-10T12:03:00.000Z");
            assertThat(db.evictArchivedRoots(CUT_OFF, backstop)).isGreaterThan(0);
            assertThat(traceIds(db)).isEmpty();
        }
    }

    /**
     * Evicting a truncated trace must leave its per-trace stats behind. {@code readStats} reports
     * {@code TraceStats.EMPTY} for a missing row — {@code spanCount 0, truncated false} — so deleting it
     * unlatches the span cap and lets a capped trace accept another full allowance. Observed live: one
     * trace reached 200,109 spans against a 100,000 cap this way.
     */
    @Test
    void keepsTheSpanCapLatchedAfterEvictingATruncatedTrace(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildCappedDoc(2);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            // Exceed the 2-span cap so the trace latches truncated.
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey("3333333333333333"), span(ROOT_START)));
                db.insert(writer, new SpanKV(childKey("4444444444444444"), span(ROOT_START)));
            });
            db.mergeComplete();
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);
            assertThat(db.evictArchivedRoots(CUT_OFF, BEFORE_ROOT_START)).isGreaterThan(0);
            assertThat(traceIds(db)).isEmpty();

            // The cap must still be latched: a further span for the same trace is rejected, so the
            // holding area does not start filling with a second allowance.
            db.write(writer -> db.insert(writer,
                    new SpanKV(childKey("5555555555555555"), span(ROOT_START))));
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
        return childKey(CHILD_SPAN);
    }

    private static SpanKey childKey(final String spanId) {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId(ROOT_SPAN).spanId(spanId).build();
    }

    private static SpanValue span(final Instant start) {
        return spanWithInsert(start, start);
    }

    /** Insert time drives lastActivityMs, which is what the quiet check ages on. */
    private static SpanValue spanWithInsert(final Instant start, final Instant insert) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(insert))
                .build();
    }

    private static PlanBDoc buildCappedDoc(final long maxSpansPerTrace) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .maxSpansPerTrace(maxSpansPerTrace)
                        .build())
                .build();
    }

    private static List<String> traceIds(final TraceDb db) {
        final TracesResultPage page = db.findTraces(new FindTraceCriteria(
                new PageRequest(0, 1000), null, null, SimpleDuration.ZERO));
        return page.getValues().stream().map(TraceRoot::getTraceId).sorted().toList();
    }
}
