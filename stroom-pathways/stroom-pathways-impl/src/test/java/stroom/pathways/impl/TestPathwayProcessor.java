/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracePersistence;
import stroom.pathways.shared.TraceWriter;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.PathwaysDb;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TraceSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

public class TestPathwayProcessor {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPathwayProcessor.class);

    @Test
    void test(@TempDir final Path traceDir,
              @TempDir final Path pathwaysDir) {
        // Read in sample data and create a map of traces.
        final PlanBDoc planBDoc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .settings(new TraceSettings.Builder().build())
                .build();
        try (final TraceDb traceDb = TraceDb
                .create(traceDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, planBDoc, false)) {
            final TracePersistence tracesStore = new TracePersistence() {
                @Override
                public ResultPage<TraceRoot> findTraces(final FindTraceCriteria criteria) {
                    return traceDb.findTraces(criteria);
                }

                @Override
                public Trace getTrace(final GetTraceRequest request) {
                    return traceDb.getTrace(request);
                }

                @Override
                public TraceWriter createWriter() {
                    return new TraceWriter() {
                        private final LmdbWriter writer = traceDb.createWriter();

                        @Override
                        public void addSpan(final Span span) {
                            traceDb.insert(writer, span);
                        }

                        @Override
                        public void close() {
                            writer.close();
                        }
                    };
                }
            };

            // Load pathways DB for doc
            final PathwaysDb pathwaysDb = PathwaysDb
                    .create(pathwaysDir, BYTE_BUFFERS, false);

            // Insert traces
            new TraceLoader().load(tracesStore);

            // Build and test pathways
            testPathways(pathwaysDb, traceDb);

            // Insert one more trace
            new TraceLoader().addOneMore(tracesStore);

            // Build and test more pathways
            testPathways(pathwaysDb, traceDb);
        }
    }

    void testPathways(final PathwaysDb pathwaysDb,
                      final TraceDb traceDb) {
        final MessageReceiver messageReceiver = (severity, message) -> {
            switch (severity) {
                case INFO -> LOGGER.info(message);
                case WARNING -> LOGGER.warn(message);
                case ERROR, FATAL_ERROR -> LOGGER.error(message);
            }
        };

        try (final LmdbWriter writer = pathwaysDb.createWriter()) {
            final TraceProcessor traceProcessor =
                    new TraceProcessor(BYTE_BUFFERS, new PathwaySerde(BYTE_BUFFER_FACTORY));
            traceDb.iterateTraces((traceId, function) ->
                    traceProcessor.processTrace(writer,
                            pathwaysDb,
                            traceId,
                            function,
                            PathwaysDoc.builder().uuid(UUID.randomUUID().toString()).build(),
                            messageReceiver));
            writer.commit();
        }
    }
}

