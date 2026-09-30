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
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
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
import stroom.util.shared.PageRequest;
import stroom.util.shared.time.SimpleDuration;

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
 * Integration tests for {@code TraceDb.publish} — the single publish path for a trace store.
 *
 * <p>Every trace with a real root is staged into the bucket for its root's START time, whatever the
 * individual spans' timestamps. Its non-root spans are removed from the holding area; the root itself stays
 * until it is older than the cutoff, so late spans keep finding a real root. A trace whose only root is
 * synthesized waits in the holding area until its real root arrives or the cut-off passes.
 *
 * <p>Buckets are labelled by day throughout, which is what the docs these tests build default to.
 */
class TestPublish {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    /** Anything whose root start time is strictly before this instant is archived. */
    private static final Instant CUTOFF = Instant.parse("2024-02-01T00:00:00.000Z");
    private static final Instant AFTER_CUTOFF = Instant.parse("2024-03-20T12:00:00.000Z");

    // 16-byte trace ids (32 hex chars) and 8-byte span ids (16 hex chars).
    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TRACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TRACE_C = "cccccccccccccccccccccccccccccccc";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    // -----------------------------------------------------------------------
    // One bucket per trace, keyed by the root's start time
    // -----------------------------------------------------------------------

    /**
     * Every span of a trace goes to the bucket for its ROOT's start time, whatever the span's own
     * timestamps. Here the root starts 2024-01-10 and a child arrives 2024-01-12: one bucket, both spans.
     * Bucketing the child by its own insert time instead would split the trace across two.
     */
    @Test
    void wholeTraceGoesToTheRootStartBucket(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant rootTime = Instant.parse("2024-01-10T09:00:00.000Z");
        final Instant childInsert = Instant.parse("2024-01-12T09:00:00.000Z");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(rootTime, rootTime)));
                db.insert(writer, new SpanKV(childKey(TRACE_A), span(childInsert, childInsert)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, archiveBaseDir);
        }

        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs.stream().map(p -> p.getFileName().toString()).toList())
                .as("one bucket, the root's start day")
                .containsExactly("2024-01-10");

        // The delta carries spans only — the bucket derives its own root when pushArchive merges it in —
        // so assert via getTrace, which assembles from a span prefix scan and needs no root.
        try (final TraceDb delta =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(spanCount(delta.getTrace(HexStringUtil.decode(TRACE_A))))
                    .as("root span and child both staged")
                    .isEqualTo(2);
        }
    }

    // -----------------------------------------------------------------------
    // The stored root outlives the trace's spans, until the cut-off
    // -----------------------------------------------------------------------

    /**
     * Publishing takes every span it stages, the root span included. The stored root entry stays behind, still
     * flagged as a real root and with the same start time, so a late span attaches to a rooted trace and
     * buckets by the same day rather than re-deriving an orphan.
     */
    @Test
    void spansAllArchivedButTheStoredRootRemains(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
                db.insert(writer, new SpanKV(childKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, archiveBaseDir);
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey(TRACE_A))).as("root span archived out too").isNull();
            assertThat(db.get(childKey(TRACE_A))).as("child archived out").isNull();

            final List<TraceRoot> roots = roots(db);
            assertThat(roots).as("stored root entry retained").hasSize(1);
            assertThat(roots.getFirst().isOrphan())
                    .as("still a real root — not downgraded now its root span has gone")
                    .isFalse();
            assertThat(roots.getFirst().getStartTime())
                    .as("start time unchanged, so a late span buckets to the same day")
                    .isEqualTo(NanoTimeUtil.fromInstant(AFTER_CUTOFF));
        }
    }

    // -----------------------------------------------------------------------
    // A pass only stages a trace that has spans it has not taken
    // -----------------------------------------------------------------------

    /**
     * A second pass with nothing new stages nothing. Staging a trace that is only waiting out the cut-off
     * would put its bucket back in play, and pushing a bucket copies the whole thing down and back up.
     */
    @Test
    void secondPassStagesNothingWithoutNewSpans(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path firstArchive = Files.createDirectory(tempDir.resolve("archive1"));
        final Path secondArchive = Files.createDirectory(tempDir.resolve("archive2"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
                db.insert(writer, new SpanKV(childKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, firstArchive);
        }
        assertThat(listSubDirs(firstArchive)).as("first pass stages the trace").hasSize(1);

        // The root is younger than the cut-off so it is still held — the case a staging decision based on
        // the root alone would re-stage.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, secondArchive);
        }
        assertThat(listSubDirs(secondArchive))
                .as("nothing arrived since, so no bucket is put back in play")
                .isEmpty();
    }

    /** A span arriving after a pass puts its trace back in play, so late spans still reach the bucket. */
    @Test
    void lateSpanPutsTheTraceBackInPlay(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path firstArchive = Files.createDirectory(tempDir.resolve("archive1"));
        final Path secondArchive = Files.createDirectory(tempDir.resolve("archive2"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, firstArchive);
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer ->
                    db.insert(writer, new SpanKV(childKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF))));
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, secondArchive);
        }

        final List<Path> secondDirs = listSubDirs(secondArchive);
        assertThat(secondDirs.stream().map(p -> p.getFileName().toString()).toList())
                .as("the late child is staged, into the root's start bucket")
                .containsExactly("2024-03-20");
        try (final TraceDb delta =
                     TraceDb.create(secondDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(spanCount(delta.getTrace(HexStringUtil.decode(TRACE_A))))
                    .as("just the late child — the root span went with the first pass")
                    .isEqualTo(1);
        }
    }

    /**
     * A trace with nothing left to stage still retires once past the cut-off. Retirement must not follow
     * the staging decision, or an idle trace would sit in the holding area forever.
     */
    @Test
    void retiresPastTheCutOffEvenWithNothingToStage(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path firstArchive = Files.createDirectory(tempDir.resolve("archive1"));
        final Path secondArchive = Files.createDirectory(tempDir.resolve("archive2"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
                db.insert(writer, new SpanKV(childKey(TRACE_A), span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }
        // Staged while younger than the cut-off, so the root is retained and nothing is left pending.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, firstArchive);
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(db)).as("root still held after the first pass").containsExactly(TRACE_A);
        }

        // Now past the cut-off, with nothing new to stage.
        final Instant laterCutOff = Instant.parse("2024-04-01T00:00:00.000Z");
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(laterCutOff, secondArchive);
        }

        assertThat(listSubDirs(secondArchive)).as("nothing to stage").isEmpty();
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(db)).as("root retired anyway").isEmpty();
            assertThat(db.get(rootKey(TRACE_A))).as("root span removed with it").isNull();
        }
    }

    /** Past the cut-off the root goes too: root span, root entry and its index entries. */
    @Test
    void rootRetiredPastTheCutOff(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant old = Instant.parse("2024-01-10T09:00:00.000Z");
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(old, old)));
                db.insert(writer, new SpanKV(childKey(TRACE_A), span(old, old)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.publish(CUTOFF, archiveBaseDir))
                    .as("two spans plus the root row")
                    .isGreaterThanOrEqualTo(3);
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey(TRACE_A))).as("root span gone").isNull();
            assertThat(traceIds(db)).as("root entry gone").isEmpty();
        }
    }

    /**
     * Staging is not age-gated — a recent trace is staged so its spans reach the archive promptly, which is
     * what makes the archive the queryable copy. Only retirement of the root waits for the cut-off.
     */
    @Test
    void everyRootIsStaged_notOnlyAgedOnes(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant agedStart = Instant.parse("2024-01-10T09:00:00.000Z");
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(agedStart, agedStart)));
                db.insert(writer, new SpanKV(rootKey(TRACE_B), span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, archiveBaseDir);
        }

        assertThat(listSubDirs(archiveBaseDir).stream().map(p -> p.getFileName().toString()).toList())
                .as("both traces staged, each to its own start-time bucket")
                .containsExactly("2024-01-10", "2024-03-20");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(db)).as("only the aged root is retired").containsExactly(TRACE_B);
        }
    }

    // -----------------------------------------------------------------------
    // Rootless traces need no separate sweep
    // -----------------------------------------------------------------------

    /**
     * A trace whose root span never arrived gets a synthesized root from {@code mergeComplete}, and that
     * root has a start time like any other — so the single path archives it with no special case. The span
     * has to arrive via {@code merge}, which is how ingest reaches a shard; a bare {@code insert} queues no
     * root rebuild.
     */
    @Test
    void rootlessTraceIsArchivedByTheSamePath(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path batch = Files.createDirectory(tempDir.resolve("batch"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant old = Instant.parse("2024-01-15T12:00:00.000Z");
        try (final TraceDb db = TraceDb.create(batch, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(childKey(TRACE_A), span(old, old))));
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.merge(batch);
            db.mergeComplete();
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.publish(CUTOFF, archiveBaseDir))
                    .isGreaterThanOrEqualTo(1);
        }

        assertThat(listSubDirs(archiveBaseDir)).hasSize(1);
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(childKey(TRACE_A))).as("span archived out of the live shard").isNull();
        }
    }

    /**
     * Each delta must hold only the traces assigned to its own label. Staging seeks to each selected trace's
     * key prefix, so a wrong prefix would copy a neighbouring trace into the wrong bucket rather than skip
     * it — and nothing else asserts that a bucket is free of other buckets' traces.
     */
    @Test
    void eachDeltaHoldsOnlyItsOwnTraces(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant january = Instant.parse("2024-01-10T09:00:00.000Z");
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(january, january)));
                db.insert(writer, new SpanKV(childKey(TRACE_A), span(january, january)));
                db.insert(writer, new SpanKV(rootKey(TRACE_B), span(AFTER_CUTOFF, AFTER_CUTOFF)));
                db.insert(writer, new SpanKV(childKey(TRACE_B), span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, archiveBaseDir);
        }

        assertDeltaHoldsOnly(archiveBaseDir.resolve("2024-01-10"), doc, TRACE_A, TRACE_B);
        assertDeltaHoldsOnly(archiveBaseDir.resolve("2024-03-20"), doc, TRACE_B, TRACE_A);
    }

    private static void assertDeltaHoldsOnly(final Path deltaDir,
                                             final PlanBDoc doc,
                                             final String presentTraceId,
                                             final String absentTraceId) {
        try (final TraceDb delta = TraceDb.create(deltaDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(spanCount(delta.getTrace(HexStringUtil.decode(presentTraceId))))
                    .as("root span and child of " + presentTraceId)
                    .isEqualTo(2);
            assertThat(delta.findTrace(HexStringUtil.decode(absentTraceId)))
                    .as(absentTraceId + " is assigned to the other label")
                    .isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // A synthesized root is waited on, not archived
    // -----------------------------------------------------------------------

    /**
     * A synthesized root's start time is the earliest span's, not the root span's, so archiving on it would
     * bucket the trace by a start time the real root then contradicts. Inside the cut-off the trace waits.
     */
    @Test
    void orphanInsideTheCutOffIsNotStaged(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        mergeChild(dbDir, doc, tempDir, "b1", CHILD_SPAN, AFTER_CUTOFF);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.publish(CUTOFF, archiveBaseDir)).isZero();
        }

        assertThat(listSubDirs(archiveBaseDir)).as("no bucket for a trace still awaiting its root").isEmpty();
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(childKeyAt(TRACE_A, AFTER_CUTOFF))).as("span held back").isNotNull();
        }
    }

    /** Once the cut-off says the real root will never arrive, the same trace is archived as an orphan. */
    @Test
    void orphanPastTheCutOffIsStaged(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        mergeChild(dbDir, doc, tempDir, "b1", CHILD_SPAN, AFTER_CUTOFF);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.publish(Instant.parse("2024-06-01T00:00:00.000Z"), archiveBaseDir))
                    .as("span plus the retired root row")
                    .isGreaterThanOrEqualTo(2);
        }

        assertThat(listSubDirs(archiveBaseDir).stream().map(p -> p.getFileName().toString()).toList())
                .containsExactly("2024-03-20");
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(db)).as("root retired with the span").isEmpty();
        }
    }

    /**
     * Why the wait matters. A child arriving first synthesizes a root starting on 2024-03-20; the real root
     * starts half an hour earlier, on 2024-03-19. Were the child staged on the first cycle it would be
     * deleted locally, leaving the root span alone to reach the 2024-03-19 bucket — one trace reported
     * two ways depending on which bucket a query opened.
     */
    @Test
    void traceLandsWhollyInTheRealRootsBucket_whenTheRootArrivesLater(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant childStart = Instant.parse("2024-03-20T00:30:00.000Z");
        final Instant rootStart = Instant.parse("2024-03-19T23:30:00.000Z");

        mergeChild(dbDir, doc, tempDir, "b1", CHILD_SPAN, childStart);
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.publish(CUTOFF, archiveBaseDir);
        }
        assertThat(listSubDirs(archiveBaseDir)).as("nothing staged while the root is outstanding").isEmpty();

        final Path rootBatch = Files.createDirectory(tempDir.resolve("b2"));
        try (final TraceDb db = TraceDb.create(rootBatch, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(w -> db.insert(w, new SpanKV(rootKeyAt(TRACE_A, rootStart), span(rootStart, rootStart))));
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.merge(rootBatch);
            db.mergeComplete();
            db.publish(CUTOFF, archiveBaseDir);
        }

        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs.stream().map(p -> p.getFileName().toString()).toList())
                .as("one bucket, the real root's start day")
                .containsExactly("2024-03-19");
        try (final TraceDb delta =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(spanCount(delta.getTrace(HexStringUtil.decode(TRACE_A))))
                    .as("root span and child together")
                    .isEqualTo(2);
        }
    }

    /** With no root rows at all there is nothing to stage. Retention is what collects such spans. */
    @Test
    void nothingStaged_whenNoTraceHasARoot(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        // insert() writes a root entry only for a root span, and queues no rebuild, so this leaves a span
        // with no root row at all.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(childKey(TRACE_A), span(CUTOFF, CUTOFF))));
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.publish(CUTOFF, archiveBaseDir)).isZero();
        }
        assertThat(listSubDirs(archiveBaseDir)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Repeated publishing of the same day must MERGE, not overwrite
    // -----------------------------------------------------------------------

    /**
     * Guards against silent data loss on repeated publishing: when a bucket for a
     * date already exists on the shared store, a second archive batch for that same date
     * must be <em>merged</em> into it, not overwrite it. Raw-copying the new batch's
     * {@code data.mdb} over the existing one would discard every trace archived by
     * earlier runs for that date.
     */
    @Test
    void pushArchive_mergesRepeatedBatchesForSameDay_ratherThanOverwriting(
            @TempDir final Path tempDir) throws IOException {
        final Path shared = Files.createDirectory(tempDir.resolve("shared"));
        final PlanBDoc doc = buildSharedDoc(shared);
        final String dayLabel = "2024-01-10";
        final Instant day = Instant.parse("2024-01-10T09:00:00.000Z");

        // First archive batch for the day: trace A only.
        final Path batch1 = Files.createDirectory(tempDir.resolve("batch1"));
        try (final TraceDb db = TraceDb.create(batch1, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(rootKey(TRACE_A), span(day, day))));
        }
        // Second archive batch for the SAME day: trace B only.
        final Path batch2 = Files.createDirectory(tempDir.resolve("batch2"));
        try (final TraceDb db = TraceDb.create(batch2, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(rootKey(TRACE_B), span(day, day))));
        }

        // pushArchive does not use NodeInfo, so null is fine here.
        final SharedFileStorePublisher publisher = new SharedFileStorePublisher(
                null, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, new PlanBPaths(tempDir.resolve("local_state")));
        publisher.pushArchive(doc, 0, new StagedArchive(dayLabel, batch1));
        publisher.pushArchive(doc, 0, new StagedArchive(dayLabel, batch2)); // must MERGE

        // The shared bucket must contain BOTH traces — an overwrite would leave only TRACE_B.
        final Path bucket = shared
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(0))
                .resolve(dayLabel);
        assertThat(bucket.resolve(PlanBConstants.VERSION_FILE_NAME)).exists();
        try (final TraceDb archive = TraceDb.create(bucket, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_A, TRACE_B);
        }
    }

    // -----------------------------------------------------------------------
    // Large trace is archived by streaming, not by buffering every span
    // -----------------------------------------------------------------------

    /**
     * Archiving a trace with a large span count must not buffer all its spans in heap —
     * collecting every archived span's raw bytes before writing would OOM on a large or
     * open-ended trace. This archives a many-thousand-span trace and asserts the whole
     * trace is streamed into the archive intact and removed from the live shard,
     * exercising the streaming write path at scale.
     */
    @Test
    void archivesLargeTraceByStreaming_notBuffering(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();
        final Instant start = Instant.parse("2024-01-15T12:00:00.000Z");
        final int childCount = 5_000;

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey(TRACE_A), span(start, start)));
                for (int i = 0; i < childCount; i++) {
                    db.insert(writer, new SpanKV(
                            spanKey(TRACE_A, ROOT_SPAN, String.format("%016x", i + 16)),
                            span(start, start)));
                }
            });
        }

        final long archived;
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            archived = db.publish(CUTOFF, archiveBaseDir);
        }
        // root + all children (+ the root-DBI entry) archived and deleted.
        assertThat(archived).isGreaterThanOrEqualTo(childCount + 1);

        // Live shard emptied of the trace.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey(TRACE_A))).isNull();
            assertThat(traceIds(db)).isEmpty();
        }

        // The delta holds the whole trace: root span + every child, nothing lost in streaming. Assert via
        // findTrace, which assembles from a span prefix scan — the delta carries no root entry of its own.
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final Optional<Trace> trace = archive.findTrace(HexStringUtil.decode(TRACE_A));
            assertThat(trace).isPresent();
            assertThat(spanCount(trace.get())).isEqualTo(childCount + 1);
        }
    }

    // -----------------------------------------------------------------------
    // mergeComplete recomputes derived root fields over the fully-merged trace
    // -----------------------------------------------------------------------

    /**
     * A trace root's derived fields (depth/services/totalSpans) must be recomputed over the
     * fully-merged span set at {@code mergeComplete()}, not left at the value computed from the
     * single batch that carried the root span — which for a root-only batch reports depth 1
     * however deep the trace really is. Verified regardless of the order batches are merged in.
     */
    @Test
    void mergeComplete_recomputesRootDepthOverFullyMergedTrace(@TempDir final Path tempDir)
            throws IOException {
        assertDepthRebuilt(Files.createDirectory(tempDir.resolve("descendants-first")), true);
        assertDepthRebuilt(Files.createDirectory(tempDir.resolve("root-first")), false);
    }

    private void assertDepthRebuilt(final Path base, final boolean descendantsFirst) throws IOException {
        final PlanBDoc doc = buildDoc();
        final Instant t = Instant.parse("2024-03-01T09:00:00.000Z");
        final String root = "1111111111111111";
        final String child = "2222222222222222";
        final String grandchild = "3333333333333333";

        // Source containing ONLY the root span -> its per-batch trace-root depth is 1.
        final Path rootSrc = Files.createDirectory(base.resolve("root-src"));
        try (final TraceDb db = TraceDb.create(rootSrc, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(w -> db.insert(w, new SpanKV(spanKey(TRACE_A, "", root), span(t, t))));
        }
        // Source containing the two descendants (no root span -> no trace-root entry).
        final Path descSrc = Files.createDirectory(base.resolve("desc-src"));
        try (final TraceDb db = TraceDb.create(descSrc, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(w -> {
                db.insert(w, new SpanKV(spanKey(TRACE_A, root, child), span(t, t)));
                db.insert(w, new SpanKV(spanKey(TRACE_A, child, grandchild), span(t, t)));
            });
        }

        final Path target = Files.createDirectory(base.resolve("target"));
        try (final TraceDb db = TraceDb.create(target, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            if (descendantsFirst) {
                db.merge(descSrc);
                db.merge(rootSrc);
            } else {
                db.merge(rootSrc);
                db.merge(descSrc);
            }
            // Per-batch the stored root reflects only the root span's batch (depth 1);
            // mergeComplete must recompute it over root + child + grandchild.
            db.mergeComplete();
        }

        try (final TraceDb db = TraceDb.create(target, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final TracesResultPage page = db.findTraces(new FindTraceCriteria(
                    new PageRequest(0, 100), null, null, SimpleDuration.ZERO));
            assertThat(page.getValues()).hasSize(1);
            final TraceRoot rebuilt = page.getValues().getFirst();
            assertThat(rebuilt.getTraceId()).isEqualTo(TRACE_A);
            assertThat(rebuilt.getDepth()).isEqualTo(3);      // root -> child -> grandchild
            assertThat(rebuilt.getTotalSpans()).isEqualTo(3);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static SpanKey spanKey(final String traceId, final String parentSpanId, final String spanId) {
        return SpanKey.builder().traceId(traceId).parentSpanId(parentSpanId).spanId(spanId).build();
    }

    private static PlanBDoc buildSharedDoc(final Path sharedPath) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
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

    private static SpanKey rootKey(final String traceId) {
        return SpanKey.builder().traceId(traceId).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanKey childKey(final String traceId) {
        return SpanKey.builder().traceId(traceId).parentSpanId(ROOT_SPAN).spanId(CHILD_SPAN).build();
    }

    // Production builds keys with SpanKey.create(span), so the start time is on the key too — which is what
    // the earliest-start scan behind a synthesized root reads.
    private static SpanKey keyAt(final String traceId,
                                 final String parentSpanId,
                                 final String spanId,
                                 final Instant start) {
        return SpanKey.builder()
                .traceId(traceId)
                .parentSpanId(parentSpanId)
                .spanId(spanId)
                .startTimeUnixNano(Long.toString(NanoTimeUtil.fromInstant(start).toEpochNanos()))
                .build();
    }

    private static SpanKey rootKeyAt(final String traceId, final Instant start) {
        return keyAt(traceId, "", ROOT_SPAN, start);
    }

    private static SpanKey childKeyAt(final String traceId, final Instant start) {
        return keyAt(traceId, ROOT_SPAN, CHILD_SPAN, start);
    }

    // Only merge queues the root rebuild that synthesizes a root for a rootless trace; a bare insert does not.
    private static void mergeChild(final Path dbDir,
                                   final PlanBDoc doc,
                                   final Path tempDir,
                                   final String batchName,
                                   final String spanId,
                                   final Instant start) throws IOException {
        final Path batch = Files.createDirectory(tempDir.resolve(batchName));
        try (final TraceDb db = TraceDb.create(batch, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer,
                    new SpanKV(keyAt(TRACE_A, ROOT_SPAN, spanId, start), span(start, start))));
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.merge(batch);
            db.mergeComplete();
        }
    }

    /** A span value with the given start time (also used as end time) and insert time. */
    private static SpanValue span(final Instant start, final Instant insert) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(insert))
                .build();
    }

    /** Stored roots returned by a default (start-time-sorted) findTraces over the whole shard. */
    private static List<TraceRoot> roots(final TraceDb db) {
        return db.findTraces(new FindTraceCriteria(
                new PageRequest(0, 1000), null, null, SimpleDuration.ZERO)).getValues();
    }

    /** Trace ids returned by a default (start-time-sorted) findTraces over the whole shard. */
    private static List<String> traceIds(final TraceDb db) {
        return roots(db).stream().map(TraceRoot::getTraceId).sorted().toList();
    }

    private static int spanCount(final Trace trace) {
        return trace.getParentSpanIdMap().values().stream().mapToInt(List::size).sum();
    }

    private static List<Path> listSubDirs(final Path dir) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (final var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).sorted().forEach(result::add);
        }
        return result;
    }
}
