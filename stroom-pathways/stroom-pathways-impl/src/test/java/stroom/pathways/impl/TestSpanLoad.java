package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.ExportTraceServiceRequest;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.ResourceSpans;
import stroom.pathways.shared.otel.trace.ScopeSpans;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestSpanLoad {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSpanLoad.class);
    private static final ObjectMapper MAPPER = createMapper(true);

    @Test
    void testLoadStandard() {

        // Read in sample data and create a map of traces.
        final Map<String, Trace> traceMap = new HashMap<>();
        for (int i = 1; i <= 13; i++) {
            final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
            loadData(path, traceMap);
        }

        // Output the tree for each trace.
        for (final Trace trace : traceMap.values()) {
            LOGGER.info("\n" + trace.toString());
        }

        // Construct known paths for all traces.
        final Map<PathKey, PathNode> roots = buildPathways(traceMap.values());

        // Output found pathways.
        for (final PathNode node : roots.values()) {
            LOGGER.info("\n" + node.toString());
        }

        // Validate traces against known paths.
        validate(traceMap.values(), roots, new HashMap<>());

        // Introduce an invalid pathway.
        for (int i = 14; i <= 17; i++) {
            final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
            loadData(path, traceMap);
        }
        assertThrows(RuntimeException.class, () -> validate(traceMap.values(), roots, new HashMap<>()));
    }

    private Map<PathKey, PathNode> buildPathways(final Collection<Trace> traces) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoTime.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final NodeMutator nodeMutator = new NodeMutatorImpl(spanComparator, pathKeyFactory);
        final Map<PathKey, PathNode> roots = new HashMap<>();
        final Map<PathKey, Map<String, Map<PathKey, PathNodeList>>> maps = new HashMap<>();
        for (final Trace trace : traces) {
            final Span root = trace.getRoot();
            final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
            final PathNode node = roots.computeIfAbsent(pathKey, k -> new PathNode(root.getName()));
            final Map<String, Map<PathKey, PathNodeList>> map = maps.computeIfAbsent(pathKey, k -> new HashMap<>());
            node.addSpan(root);
            walk(trace, root, node, nodeMutator, map);
        }
        return roots;
    }

    private void validate(final Collection<Trace> traces,
                          final Map<PathKey, PathNode> roots,
                          final Map<String, Map<PathKey, PathNodeList>> map) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoTime.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final NodeMutator nodeValidator = new NodeValidator(spanComparator, pathKeyFactory);
        for (final Trace trace : traces) {
            final Span root = trace.getRoot();
            final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
            final PathNode node = roots.get(pathKey);
            if (node == null) {
                throw new RuntimeException("Invalid root path: " + pathKey);
            }
            walk(trace, root, node, nodeValidator, map);
        }
    }

    private void loadData(final Path path,
                          final Map<String, Trace> traceMap) {
        try (final BufferedReader lineReader = Files.newBufferedReader(path)) {
            final String line = lineReader.readLine();
            final ExportTraceServiceRequest exportRequest =
                    MAPPER.readValue(line, ExportTraceServiceRequest.class);
            for (final ResourceSpans resourceSpans : NullSafe.list(exportRequest.getResourceSpans())) {
                for (final ScopeSpans scopeSpans : NullSafe.list(resourceSpans.getScopeSpans())) {
                    for (final Span span : NullSafe.list(scopeSpans.getSpans())) {
                        traceMap.computeIfAbsent(span.getTraceId(), Trace::new).addSpan(span);
                    }
                }
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void walk(final Trace trace,
                      final Span parentSpan,
                      final PathNode parentNode,
                      final NodeMutator nodeMutator,
                      final Map<String, Map<PathKey, PathNodeList>> map) {
        final List<Span> children = trace.getChildren(parentSpan);
        if (!children.isEmpty()) {
            final PathNodeList targets = nodeMutator.update(children, parentNode, map);

            for (int i = 0; i < targets.getNodes().size(); i++) {
                // Add additional span info if wanted.
                final PathNode target = targets.getNodes().get(i);
                final Span span = children.get(i);
                target.addSpan(span);

                // Follow the path deeper.
                walk(trace, span, target, nodeMutator, map);
            }
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
