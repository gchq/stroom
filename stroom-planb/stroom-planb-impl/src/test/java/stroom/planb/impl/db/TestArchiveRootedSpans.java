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
import stroom.pathways.shared.otel.trace.Trace;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@code TraceDb.archiveRootedSpans}, which makes the archive the queryable copy of a trace.
 *
 * <p>Unlike {@code archiveOldData}, this runs every merge cycle rather than waiting out the archival
 * lead time. Its contract is:
 * <ul>
 *   <li>A rooted trace's spans go to the bucket labelled by the <em>root's start time</em>, so a trace
 *       whose spans straddle a bucket boundary stays whole in one bucket.</li>
 *   <li>The root span is <em>copied</em>, not moved: the bucket needs it to rebuild a real root, and
 *       the holding area needs it so late spans keep finding a real (non-orphan) root.</li>
 *   <li>Only non-root spans are removed from the holding area.</li>
 *   <li>Every rooted trace is selected on every run, including one whose only span is its root —
 *       selecting only traces with children would lose those entirely.</li>
 *   <li>Orphan traces (no root span) are left alone — they have no bucket to go to.</li>
 * </ul>
 */
class TestArchiveRootedSpans {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    /** Root starts on the 10th; the child is inserted on the 20th, in a different DAY bucket. */
    private static final Instant ROOT_START = Instant.parse("2024-01-10T12:00:00.000Z");
    private static final Instant CHILD_INSERT = Instant.parse("2024-01-20T12:00:00.000Z");
    private static final String ROOT_START_LABEL = "2024-01-10";

    // -----------------------------------------------------------------------
    // Bucketing, and what moves vs what stays
    // -----------------------------------------------------------------------

    /**
     * Both spans go to the ROOT'S START bucket even though the child was inserted ten days later, so
     * the trace is not split across buckets by insert time.
     */
    @Test
    void archivesRootedTraceToTheRootStartBucket(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START, ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START, CHILD_INSERT)));
            });
            db.mergeComplete();

            assertThat(db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir)).isEqualTo(1);
        }

        assertThat(labels(archiveBaseDir)).containsExactly(ROOT_START_LABEL);
        // The delta carries the whole trace: root span AND child.
        assertThat(spanCount(archiveBaseDir.resolve(ROOT_START_LABEL), doc)).isEqualTo(2);
    }

    /**
     * The root span stays behind while its children leave. Retaining it is what stops
     * {@code buildRootFromStats} synthesizing an orphan over the real root on a later cycle, which
     * would break both late-span routing and the archival age axis.
     */
    @Test
    void keepsTheRootSpanAndRemovesOnlyChildren(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START, ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START, CHILD_INSERT)));
            });
            db.mergeComplete();
            db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir);

            // One span left in the holding area, and it is the root.
            final Optional<Trace> live = db.findTrace(HexStringUtil.decode(TRACE_A));
            assertThat(live).isPresent();
            assertThat(spanCount(live.get())).isEqualTo(1);
            assertThat(live.get().root()).isNotNull();
            // The root entry is still there and still a real (non-orphan) root.
            assertThat(db.rootSpan(HexStringUtil.decode(TRACE_A))).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // Selection: every rooted trace, every run
    // -----------------------------------------------------------------------

    /**
     * A second run re-sends the trace, because selection is every rooted trace rather than only those
     * with spans still to send. Nothing is removed the second time — the children have already gone — and
     * re-sending is harmless, since span puts use {@code MDB_NOOVERWRITE} and the bucket recomputes its
     * own root. The delta is then just the retained root span.
     */
    @Test
    void reSendsAnAlreadyArchivedTraceWithoutRemovingAnything(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path firstBase = Files.createDirectory(tempDir.resolve("archive1"));
        final Path secondBase = Files.createDirectory(tempDir.resolve("archive2"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(), span(ROOT_START, ROOT_START)));
                db.insert(writer, new SpanKV(childKey(), span(ROOT_START, CHILD_INSERT)));
            });
            db.mergeComplete();

            assertThat(db.archiveRootedSpans(ArchivalGranularity.DAY, firstBase)).isEqualTo(1);

            // Selected again, but the child has already gone so nothing is removed.
            assertThat(db.archiveRootedSpans(ArchivalGranularity.DAY, secondBase)).isEqualTo(0);
        }

        // Still staged, carrying only the root span that stays behind in the holding area.
        assertThat(labels(secondBase)).containsExactly(ROOT_START_LABEL);
        assertThat(spanCount(secondBase.resolve(ROOT_START_LABEL), doc)).isEqualTo(1);
    }

    /**
     * A trace whose only span is its root must still reach its bucket. It has no children, so a selection
     * rule based on children alone would never send it — and it would then satisfy every
     * {@code evictArchivedRoots} condition and be deleted having never been archived.
     */
    @Test
    void archivesARootOnlyTrace(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(rootKey(), span(ROOT_START, ROOT_START))));
            db.mergeComplete();

            // No children, so nothing is deleted from the holding area — but the delta must still be
            // written, which is what makes the trace queryable.
            assertThat(db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir))
                    .isEqualTo(0);
        }

        assertThat(labels(archiveBaseDir)).containsExactly(ROOT_START_LABEL);
        assertThat(spanCount(archiveBaseDir.resolve(ROOT_START_LABEL), doc)).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Orphans are not this operation's business
    // -----------------------------------------------------------------------

    /**
     * A trace with no root span has no start time, so no bucket can be derived for it. It must be left
     * for {@code archiveOldData}'s insert-time sweep rather than guessed at here.
     */
    @Test
    void leavesOrphanTracesAlone(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            // Child only — no root span ever arrives.
            db.write(writer -> db.insert(writer, new SpanKV(childKey(), span(ROOT_START, CHILD_INSERT))));
            db.mergeComplete();

            assertThat(db.archiveRootedSpans(ArchivalGranularity.DAY, archiveBaseDir)).isEqualTo(0);
            // The orphan's span is still in the holding area.
            assertThat(db.count()).isEqualTo(1);
        }

        assertThat(labels(archiveBaseDir)).isEmpty();
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

    private static SpanValue span(final Instant start, final Instant insert) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(insert))
                .build();
    }

    private static int spanCount(final Trace trace) {
        return trace.getParentSpanIdMap().values().stream().mapToInt(List::size).sum();
    }

    /** Total spans held in a staged delta env. */
    private static int spanCount(final Path deltaDir, final PlanBDoc doc) {
        try (final TraceDb delta = TraceDb.create(
                deltaDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            return (int) delta.count();
        }
    }

    private static List<String> labels(final Path archiveBaseDir) throws IOException {
        final List<String> names = new ArrayList<>();
        try (final var stream = Files.list(archiveBaseDir)) {
            stream.filter(Files::isDirectory).sorted()
                    .forEach(p -> names.add(p.getFileName().toString()));
        }
        return names;
    }
}
