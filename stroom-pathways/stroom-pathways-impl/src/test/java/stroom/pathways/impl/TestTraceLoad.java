package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracePersistence;
import stroom.pathways.shared.TraceWriter;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class TestTraceLoad {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    @Test
    void testLmdb(@TempDir final Path tempDir) {
        // Read in sample data and create a map of traces.
        final PlanBDoc planBDoc = PlanBDoc.builder().settings(new TraceSettings.Builder().build()).build();
        try (final TraceDb traceDb = TraceDb
                .create(tempDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, planBDoc, false)) {
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

            new TraceLoader().load(tracesStore);
        }
    }

    @Test
    void testMemory() {
        final TracePersistence persistence = new TracePersistenceMemory();
        new TraceLoader().load(persistence);
    }
}

