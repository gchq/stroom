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
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.TimeFilter;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code TraceDb.histogram} counts traces into equal buckets laid out from the origin and width it is
 * handed. The traces screen draws its axis from those same two numbers, so a bucket must hold exactly
 * the traces whose start times fall in the span the axis says it covers.
 */
class TestTraceHistogram {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final String ROOT_SPAN = "1111111111111111";
    private static final Instant ORIGIN = Instant.parse("2026-01-15T00:00:00.000Z");
    private static final long WIDTH_MS = Duration.ofMinutes(5).toMillis();

    @Test
    void tracesLandInTheBucketHoldingTheirStartTime(@TempDir final Path tempDir) throws IOException {
        // One trace 1 minute in (bucket 0), two 7 minutes in (bucket 1), one 21 minutes in (bucket 4).
        final Path dbDir = write(tempDir, 1, 7, 7, 21);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc(), true)) {
            final long[] counts = db.histogram(filter(6), WIDTH_MS, 72, null);
            assertThat(counts[0]).isEqualTo(1L);
            assertThat(counts[1]).isEqualTo(2L);
            assertThat(counts[2]).isZero();
            assertThat(counts[3]).isZero();
            assertThat(counts[4]).isEqualTo(1L);
        }
    }

    @Test
    void movingTheWindowEndDoesNotMoveTracesBetweenBuckets(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = write(tempDir, 1, 7, 7, 21);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc(), true)) {
            // The same origin and width, over a window six hours long and then one bucket longer:
            // the extra bucket is empty and every earlier count is untouched.
            final long[] sixHours = db.histogram(filter(6), WIDTH_MS, 72, null);
            final long[] longer = db.histogram(filter(6), WIDTH_MS, 73, null);

            assertThat(longer).hasSize(73);
            assertThat(longer[72]).isZero();
            for (int b = 0; b < sixHours.length; b++) {
                assertThat(longer[b]).as("bucket " + b).isEqualTo(sixHours[b]);
            }
        }
    }

    private static Path write(final Path tempDir, final int... minutesIn) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc(), false)) {
            db.write(writer -> {
                int id = 0;
                for (final int minute : minutesIn) {
                    insertRoot(db, writer, String.format("%032d", id++),
                            ORIGIN.plus(Duration.ofMinutes(minute)));
                }
            });
        }
        return dbDir;
    }

    private static TimeFilter filter(final int hours) {
        return new TimeFilter(ORIGIN.toEpochMilli(),
                ORIGIN.plus(Duration.ofHours(hours)).toEpochMilli());
    }

    private static void insertRoot(final TraceDb db,
                                   final LmdbWriter writer,
                                   final String traceId,
                                   final Instant start) {
        final SpanKey rootKey = SpanKey.builder()
                .traceId(traceId).parentSpanId("").spanId(ROOT_SPAN).build();
        final SpanValue value = SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(start))
                .build();
        db.insert(writer, new SpanKV(rootKey, value));
    }

    private static PlanBDoc doc() {
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
