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
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.ArchivalGranularity;
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
 * Integration tests for {@code TraceDb.archiveOldData}.
 *
 * <p>Archiving is age-gated on the root's own end time and ages spans by their own insert time:
 * <ul>
 *   <li>A trace's root is archived once its own end time is older than the cutoff (aged),
 *       regardless of ongoing activity — so a leaky / never-ending trace is bounded rather than
 *       kept live forever. A root whose own end is recent is retained.</li>
 *   <li>An aged root is bucketed by the root's <em>start</em> time (the query axis) and its
 *       root span rides with it; non-root spans bucket by their own insert time, so a recent
 *       child of an aged root is left behind as an orphan and swept on a later cycle.</li>
 *   <li>Each archive holds only its own bucket's roots — no cross-bucket duplication —
 *       verified by querying the archive's rebuilt sort index.</li>
 *   <li>Orphan spans (no root anywhere) are swept by insert time so the live shard stays
 *       bounded without a retention policy.</li>
 * </ul>
 *
 * <p>{@link ArchivalGranularity#DAY} is used throughout.
 */
class TestArchiveOldData {

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
    // Bucketing by root start time
    // -----------------------------------------------------------------------

    /**
     * An AGED trace (its root's own end time is older than the cutoff) is archived, and its
     * root is bucketed by the root's <em>start</em> time (the query axis), independent of when
     * the span was inserted. Here start/end = 2024-01-10 (aged) but insert = 2024-01-20: it
     * archives to the 2024-01-10 (start) bucket, not the 2024-01-20 (insert) bucket.
     */
    @Test
    void agedTrace_rootArchivedToStartBucket(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant start = Instant.parse("2024-01-10T12:00:00.000Z");
        final Instant insert = Instant.parse("2024-01-20T12:00:00.000Z"); // still before CUTOFF
        final SpanKey rootKey = rootKey(TRACE_A);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer ->
                    db.insert(writer, new SpanKV(rootKey, span(start, insert))));
        }

        final long archived;
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            archived = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        assertThat(archived).isGreaterThanOrEqualTo(1);

        // Root (and its root span) go to the START-time bucket, not the insert day.
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        assertThat(archiveDirs.getFirst().getFileName().toString()).isEqualTo("2024-01-10");

        // Gone from the live shard, present (and queryable) in the archive.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey)).isNull();
        }
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_A);
        }
    }

    /**
     * A root whose own end time is old is archived even if its span was RECEIVED recently
     * (insert time after the cutoff). Eligibility keys on the root's own end
     * ({@code rootEndTime}), not on receipt/activity, so a late-arriving span for an
     * already-aged root cannot keep the trace live. Here end = 2024-01-15 (aged) but insert =
     * AFTER_CUTOFF (recent): the root is archived to its 2024-01-15 (start/end) bucket.
     */
    @Test
    void agedRoot_archivedEvenIfReceivedRecently(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant oldEnd = Instant.parse("2024-01-15T12:00:00.000Z");
        final SpanKey rootKey = rootKey(TRACE_A);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer ->
                    db.insert(writer, new SpanKV(rootKey, span(oldEnd, AFTER_CUTOFF))));
        }

        final long archived;
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            archived = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }
        assertThat(archived).isGreaterThanOrEqualTo(1);

        assertThat(listSubDirs(archiveBaseDir).stream().map(p -> p.getFileName().toString()).toList())
                .containsExactly("2024-01-15");
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey)).as("aged root archived out of live shard").isNull();
        }
    }

    /**
     * A quiet multi-window trace fragments across dated buckets by each span's own INSERT
     * time (A), except the root span, which rides with the root entry into the root's
     * START-time bucket. Here the root (start/insert 2024-01-10) and a child (insert
     * 2024-01-12) land in two different buckets — the root's bucket holds just the root.
     */
    @Test
    void multiWindowQuietTrace_spansBucketByOwnInsertTime(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant rootTime = Instant.parse("2024-01-10T09:00:00.000Z");
        final Instant childInsert = Instant.parse("2024-01-12T09:00:00.000Z"); // still before CUTOFF
        final SpanKey rootKey = rootKey(TRACE_A);
        final SpanKey childKey = childKey(TRACE_A);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey, span(rootTime, rootTime)));
                db.insert(writer, new SpanKV(childKey, span(childInsert, childInsert)));
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        // Two buckets: the root's start bucket (root span) and the child's insert bucket.
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs.stream().map(p -> p.getFileName().toString()).toList())
                .containsExactly("2024-01-10", "2024-01-12");

        // The root bucket is queryable and holds ONLY the root span (child split off to
        // its own insert-time bucket).
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_A);
            assertThat(spanCount(archive.getTrace(HexStringUtil.decode(TRACE_A)))).isEqualTo(1);
        }
    }

    /**
     * Two traces in two different start-time buckets must produce two archives,
     * each containing ONLY its own root. This is the regression guard for the
     * previous wholesale trace-roots copy, which duplicated every root into every
     * bucket. Verified by querying each archive's rebuilt start-time sort index.
     */
    @Test
    void twoBuckets_eachArchiveHoldsOnlyItsOwnRoot(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant startA = Instant.parse("2024-01-10T09:00:00.000Z");
        final Instant startB = Instant.parse("2024-01-20T09:00:00.000Z");
        final Instant startC = Instant.parse("2024-03-05T09:00:00.000Z"); // survivor

        final SpanKey rootA = rootKey(TRACE_A);
        final SpanKey rootB = rootKey(TRACE_B);
        final SpanKey rootC = rootKey(TRACE_C);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootA, span(startA, startA)));
                db.insert(writer, new SpanKV(rootB, span(startB, startB)));
                db.insert(writer, new SpanKV(rootC, span(startC, startC)));
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(2);
        assertThat(archiveDirs.get(0).getFileName().toString()).isEqualTo("2024-01-10");
        assertThat(archiveDirs.get(1).getFileName().toString()).isEqualTo("2024-01-20");

        // Each archive is queryable via its rebuilt index and holds ONLY its root.
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.get(0), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_A);
        }
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.get(1), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_B);
        }

        // The recent trace survives in the live shard; the archived ones are gone.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootA)).isNull();
            assertThat(db.get(rootB)).isNull();
            assertThat(db.get(rootC)).isNotNull();
            assertThat(traceIds(db)).containsExactly(TRACE_C);
        }
    }

    /**
     * When a root's own end time is aged it is archived even while the trace still receives
     * children: the root and its old spans are archived (the root to its start-time bucket, the
     * old child to its insert-time bucket), and a child received after the cutoff is left behind
     * in the live shard as a parentless orphan (swept later by insert time). This is what bounds
     * a leaky / never-ending trace.
     */
    @Test
    void agedRoot_archivedWithOldSpans_recentChildOrphaned(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant rootTime = Instant.parse("2024-01-05T09:00:00.000Z");       // root end aged
        final Instant oldChildInsert = Instant.parse("2024-01-15T09:00:00.000Z"); // before CUTOFF
        final SpanKey rootKey = rootKey(TRACE_A);
        final SpanKey oldChild = spanKey(TRACE_A, ROOT_SPAN, "2222222222222222");
        final SpanKey newChild = spanKey(TRACE_A, ROOT_SPAN, "3333333333333333");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey, span(rootTime, rootTime)));
                db.insert(writer, new SpanKV(oldChild, span(oldChildInsert, oldChildInsert)));
                db.insert(writer, new SpanKV(newChild, span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        // Aged root + its root span archived; old child archived; recent child left as an orphan.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey)).as("aged root archived").isNull();
            assertThat(db.get(oldChild)).as("old child archived").isNull();
            assertThat(db.get(newChild)).as("recent child retained as orphan").isNotNull();
            assertThat(traceIds(db)).as("no queryable root remains").isEmpty();
        }
        // Root rides to its start-time bucket (2024-01-05); the old child to its insert bucket.
        assertThat(listSubDirs(archiveBaseDir).stream().map(p -> p.getFileName().toString()).toList())
                .containsExactly("2024-01-05", "2024-01-15");
    }

    // -----------------------------------------------------------------------
    // Orphan spans (no root anywhere)
    // -----------------------------------------------------------------------

    /**
     * An orphan span (child span whose root never arrived) is swept by insert
     * time so the live shard cannot grow unbounded without a retention policy. A
     * recent orphan is left in place.
     */
    @Test
    void orphanSpans_sweptByInsertTime(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant oldInsert = Instant.parse("2024-01-15T12:00:00.000Z");
        final SpanKey oldOrphan = childKey(TRACE_A);   // no root inserted for TRACE_A
        final SpanKey newOrphan = childKey(TRACE_B);   // no root inserted for TRACE_B

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(oldOrphan, span(oldInsert, oldInsert)));
                db.insert(writer, new SpanKV(newOrphan, span(AFTER_CUTOFF, AFTER_CUTOFF)));
            });
        }

        final long archived;
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            archived = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        assertThat(archived).isGreaterThanOrEqualTo(1);

        // Old orphan archived (bucket = insert day) and deleted; new orphan retained.
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        assertThat(archiveDirs.getFirst().getFileName().toString()).isEqualTo("2024-01-15");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(oldOrphan)).isNull();
            assertThat(db.get(newOrphan)).isNotNull();
        }
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(archive.get(oldOrphan)).isNotNull();
        }
    }

    @Test
    void nothingToArchive_returnsZero(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final SpanKey rootKey = rootKey(TRACE_A);
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer ->
                    db.insert(writer, new SpanKV(rootKey, span(AFTER_CUTOFF, AFTER_CUTOFF))));
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir)).isEqualTo(0);
        }

        assertThat(listSubDirs(archiveBaseDir)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Repeated archival of the same day must MERGE, not overwrite
    // -----------------------------------------------------------------------

    /**
     * Regression guard for silent data loss on repeated archival: when a bucket for a
     * date already exists on the shared store, a second archive batch for that same date
     * must be <em>merged</em> into it, not overwrite it. Previously {@code pushArchive}
     * raw-copied the new batch's {@code data.mdb} over the existing one, discarding every
     * trace archived by earlier runs for that date.
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

        // The shared bucket must contain BOTH traces — previously only TRACE_B survived.
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
     * Archiving a trace with a large span count must not buffer all its spans in heap
     * (the previous implementation collected every archived span's raw bytes into a map,
     * which OOMs on a large/open-ended trace). This archives a many-thousand-span trace
     * and asserts the whole trace is streamed into the archive intact and removed from
     * the live shard — exercising the streaming write path at scale.
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
            archived = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }
        // root + all children (+ the root-DBI entry) archived and deleted.
        assertThat(archived).isGreaterThanOrEqualTo(childCount + 1);

        // Live shard emptied of the trace.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey(TRACE_A))).isNull();
            assertThat(traceIds(db)).isEmpty();
        }

        // Archive holds the whole trace: root + every child (nothing lost in streaming).
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_A);
            final Optional<Trace> trace = archive.findTrace(HexStringUtil.decode(TRACE_A));
            assertThat(trace).isPresent();
            assertThat(spanCount(trace.get())).isEqualTo(childCount + 1);
        }
    }

    // -----------------------------------------------------------------------
    // mergeComplete recomputes derived root fields over the fully-merged trace
    // -----------------------------------------------------------------------

    /**
     * Regression guard for the "Depth = 1" bug: a trace root's derived fields
     * (depth/services/totalSpans) must be recomputed over the fully-merged span set at
     * {@code mergeComplete()}, not left at the value computed from the single batch that
     * carried the root span. Verified regardless of the order batches are merged in.
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

    /** A span value with the given start time (also used as end time) and insert time. */
    private static SpanValue span(final Instant start, final Instant insert) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(insert))
                .build();
    }

    /** Trace ids returned by a default (start-time-sorted) findTraces over the whole shard. */
    private static List<String> traceIds(final TraceDb db) {
        final TracesResultPage page = db.findTraces(new FindTraceCriteria(
                new PageRequest(0, 1000), null, null, SimpleDuration.ZERO));
        return page.getValues().stream().map(TraceRoot::getTraceId).sorted().toList();
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
