package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.ExportTraceServiceRequest;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.ResourceSpans;
import stroom.pathways.shared.otel.trace.ScopeSpans;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringIdUtil;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSpanLoad {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSpanLoad.class);
    private static final ObjectMapper MAPPER = createMapper(true);

    private static final DocRef DOC_REF = DocRef.builder().type(PathwaysDoc.TYPE).uuid("test").build();
    private static final PathwaysDoc PATHWAYS_DOC = new PathwaysDoc();

    @Test
    void testLoadStandard() {


        // Read in sample data and create a map of traces.
        final TracesStore tracesStore = new TracesStore(null);
        for (int i = 1; i <= 13; i++) {
            final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
            loadData(path, tracesStore);
        }

        // Output the tree for each trace.
        for (final Trace trace : tracesStore.getTraces(DOC_REF)) {
            LOGGER.info("\n" + trace.toString());
        }

        final StringBuilder messages = new StringBuilder();
        final MessageReceiver messageReceiver = (severity, message) -> {
            messages.append(severity.getDisplayValue());
            messages.append(": ");
            messages.append(message.get());
            messages.append("\n");
        };

        // Construct known paths for all traces.
        final Map<PathKey, PathNode> roots = buildPathways(tracesStore.getTraces(DOC_REF), messageReceiver);

        // Output found pathways.
        for (final PathNode node : roots.values()) {
            LOGGER.info("\n" + node.toString());
        }

        // Validate traces against known paths.
        validate(tracesStore.getTraces(DOC_REF), roots, messageReceiver);

        // Introduce an invalid pathway.
        for (int i = 14; i <= 17; i++) {
            final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
            loadData(path, tracesStore);
        }
        validate(tracesStore.getTraces(DOC_REF), roots, messageReceiver);
        assertThat(messages.toString()).contains("ERROR: [GET /people] thread.id '125' not equal");
    }

    private Map<PathKey, PathNode> buildPathways(final Collection<Trace> traces,
                                                 final MessageReceiver messageReceiver) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoDuration.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final TraceProcessor traceProcessor = new NodeMutatorImpl(spanComparator, pathKeyFactory);
        final Map<PathKey, PathNode> roots = new HashMap<>();
        for (final Trace trace : traces) {
            traceProcessor.process(trace, roots, messageReceiver, PATHWAYS_DOC);
        }
        return roots;
    }

    private void validate(final Collection<Trace> traces,
                          final Map<PathKey, PathNode> roots,
                          final MessageReceiver messageReceiver) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoDuration.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final TraceProcessor traceProcessor = new TraceValidator(spanComparator, pathKeyFactory);
        for (final Trace trace : traces) {
            traceProcessor.process(trace, roots, messageReceiver, PATHWAYS_DOC);
        }
    }

    private void loadData(final Path path,
                          final TracesStore tracesStore) {
        try (final BufferedReader lineReader = Files.newBufferedReader(path)) {
            final String line = lineReader.readLine();
            final ExportTraceServiceRequest exportRequest =
                    MAPPER.readValue(line, ExportTraceServiceRequest.class);
            for (final ResourceSpans resourceSpans : NullSafe.list(exportRequest.getResourceSpans())) {
                for (final ScopeSpans scopeSpans : NullSafe.list(resourceSpans.getScopeSpans())) {
                    for (final Span span : NullSafe.list(scopeSpans.getSpans())) {
                        tracesStore.addSpan(DOC_REF, span);
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }
}
