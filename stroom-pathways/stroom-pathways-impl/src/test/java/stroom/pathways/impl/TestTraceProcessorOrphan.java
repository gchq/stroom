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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A trace with spans but no root span - its root aged out or never arrived - reaches
 * {@link TraceProcessor#processTrace} and cannot be turned into a pathway, because a pathway is keyed
 * on the root's name and there is no root. What must not happen is the attempt failing in a way that
 * leaves the trace unmarked, because the job then picks it up again on every run, once a minute.
 */
class TestTraceProcessorOrphan {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final byte[] TRACE_ID = new byte[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    @Test
    void orphanTraceIsDeclinedRatherThanFailedOn(@TempDir final Path pathwaysDir) {
        final List<String> warnings = new ArrayList<>();

        try (final PathwaysDb pathwaysDb = PathwaysDb.create(pathwaysDir, BYTE_BUFFERS, false)) {
            try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                processOrphan(pathwaysDb, writer, warnings);
                writer.commit();
            }

            // The processor has to turn this away deliberately. Failing on it instead leaves nothing
            // reported here and an error with a stack trace in the log.
            assertThat(warnings)
                    .as("the trace is turned away for the stated reason")
                    .anySatisfy(message -> assertThat(message).contains("no root span"));

            // Turned away, not written off: the root may still arrive. Nothing offers a trace for
            // processing until it has a root, so no marker is needed to keep this from repeating.
            assertThat(isMarkedProcessed(pathwaysDb))
                    .as("still eligible for when its root arrives")
                    .isFalse();
        }
    }

    /**
     * The mechanism, isolated: the root of an orphan trace is null, and a one-element list holding
     * null clears the emptiness guard in {@link PathKeyFactoryImpl} and is then dereferenced.
     */
    @Test
    void pathKeyCannotBeBuiltFromAnAbsentRoot() {
        assertThatThrownBy(() -> new PathKeyFactoryImpl().create(Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anOrphanTraceHasNoRoot() {
        assertThat(orphanTrace().root()).isNull();
    }

    private static void processOrphan(final PathwaysDb pathwaysDb,
                                      final LmdbWriter writer,
                                      final List<String> warnings) {
        new TraceProcessor(BYTE_BUFFERS, new PathwaySerde(BYTE_BUFFER_FACTORY))
                .processTrace(writer,
                        pathwaysDb,
                        TRACE_ID,
                        traceId -> Optional.of(orphanTrace()),
                        PathwaysDoc.builder().uuid(UUID.randomUUID().toString()).build(),
                        (severity, message) -> {
                            if (severity == Severity.WARNING) {
                                warnings.add(message.get());
                            }
                        });
    }

    private static boolean isMarkedProcessed(final PathwaysDb pathwaysDb) {
        final ByteBuffer key = ByteBuffer.allocateDirect(TRACE_ID.length);
        key.put(TRACE_ID).flip();
        return pathwaysDb.getProcessingStatus().get(key, Objects::nonNull);
    }

    // Spans whose parent is not itself in the trace, so nothing sits under the empty-parent key that
    // Trace.root() looks for.
    private static Trace orphanTrace() {
        final Span child = Span.builder()
                .spanId("2222222222222222")
                .parentSpanId("1111111111111111")
                .name("orphaned-child")
                .build();
        final Map<String, List<Span>> byParent = Map.of(child.getParentSpanId(), List.of(child));
        return new Trace("000102030405060708090a0b0c0d0e0f", byParent);
    }
}
