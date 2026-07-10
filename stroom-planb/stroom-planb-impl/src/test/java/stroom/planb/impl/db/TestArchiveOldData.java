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
import stroom.planb.impl.data.SpanKV;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code TraceDb.archiveOldData}.
 *
 * <p>Archiving buckets whole traces by their <em>root-span start time</em> (the
 * same axis queries filter on), not by insert/merge time. These tests exercise
 * that contract:
 * <ul>
 *   <li>A trace is archived to the bucket of its root's start time even when it
 *       was inserted recently.</li>
 *   <li>Every span of a trace lands in a single bucket even when its spans
 *       straddle a bucket boundary (e.g. midnight).</li>
 *   <li>Each archive holds only its own bucket's roots — no cross-bucket
 *       duplication — verified by querying the archive's rebuilt sort index.</li>
 *   <li>Orphan spans (no root anywhere) are swept by insert time so the live
 *       shard stays bounded without a retention policy.</li>
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
     * The defining behaviour: a trace whose root <em>started</em> in an old
     * bucket but was <em>inserted</em> recently must still be archived to the
     * start-time bucket. Under insert-time bucketing it would not be archived.
     */
    @Test
    void bucketsByRootStartTime_notInsertTime(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant start = Instant.parse("2024-01-15T12:00:00.000Z");
        final SpanKey rootKey = rootKey(TRACE_A);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer ->
                    db.insert(writer, new SpanKV(rootKey, span(start, AFTER_CUTOFF))));
        }

        final long archived;
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            archived = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        assertThat(archived).isGreaterThanOrEqualTo(1);

        // Bucket is the START day, not the (recent) insert day.
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        assertThat(archiveDirs.getFirst().getFileName().toString()).isEqualTo("2024-01-15");

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
     * The inverse: a trace whose root started after the cutoff is retained even
     * if its spans were inserted long ago (insert time is irrelevant to rooted
     * traces).
     */
    @Test
    void recentRootStart_isRetained_regardlessOfInsertTime(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant recentStart = Instant.parse("2024-03-01T12:00:00.000Z");
        final Instant oldInsert = Instant.parse("2024-01-15T12:00:00.000Z");
        final SpanKey rootKey = rootKey(TRACE_A);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer ->
                    db.insert(writer, new SpanKV(rootKey, span(recentStart, oldInsert))));
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            final long archived = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
            assertThat(archived).isEqualTo(0);
        }

        assertThat(listSubDirs(archiveBaseDir)).isEmpty();
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.get(rootKey)).isNotNull();
        }
    }

    /**
     * A trace whose spans straddle a bucket boundary (root at 23:58 on day 1, a
     * child at 00:05 on day 2) must be archived whole into the single bucket of
     * the root's start time — no second bucket for the later child span.
     */
    @Test
    void straddlingSpans_allLandInRootStartBucket(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        final Instant rootStart = Instant.parse("2024-01-15T23:58:00.000Z");
        final Instant childStart = Instant.parse("2024-01-16T00:05:00.000Z");
        final SpanKey rootKey = rootKey(TRACE_A);
        final SpanKey childKey = childKey(TRACE_A);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(rootKey, span(rootStart, rootStart)));
                db.insert(writer, new SpanKV(childKey, span(childStart, childStart)));
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        // Exactly one bucket — the root's start day — holding BOTH spans.
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        assertThat(archiveDirs.getFirst().getFileName().toString()).isEqualTo("2024-01-15");

        try (final TraceDb archive =
                     TraceDb.create(archiveDirs.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final Trace trace = archive.getTrace(HexStringUtil.decode(TRACE_A));
            assertThat(spanCount(trace)).isEqualTo(2);
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
        final SharedFileStorePublisher publisher =
                new SharedFileStorePublisher(null, BYTE_BUFFERS, BYTE_BUFFER_FACTORY);
        publisher.pushArchive(doc, 0, new StagedArchive(dayLabel, batch1));
        publisher.pushArchive(doc, 0, new StagedArchive(dayLabel, batch2)); // must MERGE

        // The shared bucket must contain BOTH traces — previously only TRACE_B survived.
        final Path bucket = shared
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(0))
                .resolve(dayLabel);
        assertThat(bucket.resolve(PlanBConstants.COMPLETE_FILE_NAME)).exists();
        try (final TraceDb archive = TraceDb.create(bucket, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(traceIds(archive)).containsExactly(TRACE_A, TRACE_B);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
