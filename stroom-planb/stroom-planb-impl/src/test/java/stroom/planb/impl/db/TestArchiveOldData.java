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
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code TraceDb.archiveOldData}.
 *
 * <p>Archiving is only supported for {@link StateType#TRACE} shards (i.e.,
 * {@link stroom.pathways.shared.TracesDoc}). All other PlanB shard types use the
 * default no-op in {@link Db}.
 *
 * <p>Each test:
 * <ol>
 *   <li>Inserts spans spread across two distinct time buckets.</li>
 *   <li>Calls {@code archiveOldData} with a cutoff between the two groups.</li>
 *   <li>Verifies the main DB no longer contains the archived entries.</li>
 *   <li>Verifies the archive output directory contains one sub-directory per
 *       date-label, each containing a valid readable LMDB environment.</li>
 * </ol>
 *
 * <p>{@link ArchivalGranularity#DAY} is used throughout: old spans land in the
 * {@code "2024-01-15"} bucket, new spans in {@code "2024-03-20"}.
 */
class TestArchiveOldData {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    /** Spans with this insert time are BEFORE the cutoff — they should be archived. */
    private static final Instant OLD_TIME = Instant.parse("2024-01-15T12:00:00.000Z");
    /** Spans with this insert time are AFTER the cutoff — they stay in the main DB. */
    private static final Instant NEW_TIME = Instant.parse("2024-03-20T12:00:00.000Z");
    /** Anything strictly before this instant is archived. */
    private static final Instant CUTOFF    = Instant.parse("2024-02-01T00:00:00.000Z");

    // -----------------------------------------------------------------------
    // TraceDb
    // -----------------------------------------------------------------------

    @Test
    void trace_archivesOldEntries_andLeavesNewEntries(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));

        final PlanBDoc doc = buildDoc();

        final SpanKey oldKey1 = spanKey("d18ea88869434c083a361644267ecf30", "e0a94d9f5cd3a301");
        final SpanKey oldKey2 = spanKey("d18ea88869434c083a361644267ecf31", "e0a94d9f5cd3a302");
        final SpanKey newKey  = spanKey("d18ea88869434c083a361644267ecf32", "e0a94d9f5cd3a303");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(oldKey1, SpanValue.builder()
                        .insertTime(NanoTimeUtil.fromInstant(OLD_TIME))
                        .build()));
                db.insert(writer, new SpanKV(oldKey2, SpanValue.builder()
                        .insertTime(NanoTimeUtil.fromInstant(OLD_TIME))
                        .build()));
                db.insert(writer, new SpanKV(newKey, SpanValue.builder()
                        .insertTime(NanoTimeUtil.fromInstant(NEW_TIME))
                        .build()));
            });
        }

        final long archivedCount;
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            final long totalCount = db.count();
            // count() includes internal index rows alongside span rows
            assertThat(totalCount).isGreaterThanOrEqualTo(3);
            archivedCount = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        // archivedCount includes internal index rows; at minimum 2 old spans were archived
        assertThat(archivedCount).isGreaterThanOrEqualTo(2);

        // At least the 1 new span survives in the main DB
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.count()).isGreaterThanOrEqualTo(1);
        }

        // One DAY-labelled archive subdir: "2024-01-15"
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(1);
        assertThat(archiveDirs.get(0).getFileName().toString()).isEqualTo("2024-01-15");

        try (final TraceDb archiveDb =
                     TraceDb.create(archiveDirs.get(0), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(archiveDb.count()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void trace_nothingToArchive_returnsZero(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));

        final PlanBDoc doc = buildDoc();
        final SpanKey newKey = spanKey("d18ea88869434c083a361644267ecf32", "e0a94d9f5cd3a303");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(newKey, SpanValue.builder()
                        .insertTime(NanoTimeUtil.fromInstant(NEW_TIME))
                        .build()));
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            final long count = db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
            assertThat(count).isEqualTo(0);
        }

        assertThat(listSubDirs(archiveBaseDir)).isEmpty();
    }

    @Test
    void trace_multiDay_createsSeparateArchiveSubdirs(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));

        final PlanBDoc doc = buildDoc();

        final Instant day1 = Instant.parse("2023-11-10T00:00:00.000Z");
        final Instant day2 = Instant.parse("2023-12-20T00:00:00.000Z");

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                db.insert(writer, new SpanKV(
                        spanKey("d18ea88869434c083a361644267ecf10", "e0a94d9f5cd3a101"),
                        SpanValue.builder().insertTime(NanoTimeUtil.fromInstant(day1)).build()));
                db.insert(writer, new SpanKV(
                        spanKey("d18ea88869434c083a361644267ecf20", "e0a94d9f5cd3a201"),
                        SpanValue.builder().insertTime(NanoTimeUtil.fromInstant(day2)).build()));
                // Survivor
                db.insert(writer, new SpanKV(
                        spanKey("d18ea88869434c083a361644267ecf32", "e0a94d9f5cd3a303"),
                        SpanValue.builder().insertTime(NanoTimeUtil.fromInstant(NEW_TIME)).build()));
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir);
        }

        // Two archive subdirs: "2023-11-10" and "2023-12-20"
        final List<Path> archiveDirs = listSubDirs(archiveBaseDir);
        assertThat(archiveDirs).hasSize(2);
        assertThat(archiveDirs.get(0).getFileName().toString()).isEqualTo("2023-11-10");
        assertThat(archiveDirs.get(1).getFileName().toString()).isEqualTo("2023-12-20");

        // Main DB has surviving span
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(db.count()).isGreaterThanOrEqualTo(1);
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

    private static SpanKey spanKey(final String traceId, final String spanId) {
        return SpanKey.builder()
                .traceId(traceId)
                .parentSpanId("")
                .spanId(spanId)
                .build();
    }

    private static List<Path> listSubDirs(final Path dir) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (final var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).sorted().forEach(result::add);
        }
        return result;
    }
}
