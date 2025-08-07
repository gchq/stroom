package stroom.pathways.otel.model;

import stroom.pathways.NanoTimeUtil;
import stroom.pathways.Trace;
import stroom.pathways.model.trace.ExportTraceServiceRequest;
import stroom.pathways.model.trace.ResourceSpans;
import stroom.pathways.model.trace.ScopeSpans;
import stroom.pathways.model.trace.Span;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
        final Map<PathKey, Node> roots = buildPathways(traceMap.values());

        // Output found pathways.
        for (final Node node : roots.values()) {
            LOGGER.info("\n" + node.toString());
        }

        // Validate traces against known paths.
        validate(traceMap.values(), roots);

        // Introduce an invalid pathway.
        for (int i = 14; i <= 17; i++) {
            final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
            loadData(path, traceMap);
        }
        assertThrows(RuntimeException.class, () -> {
            validate(traceMap.values(), roots);
        });
    }

    private Map<PathKey, Node> buildPathways(final Collection<Trace> traces) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(Duration.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final NodeMutator nodeMutator = new NodeMutatorImpl(spanComparator, pathKeyFactory);
        final Map<PathKey, Node> roots = new HashMap<>();
        for (final Trace trace : traces) {
            final Span root = trace.getRoot();
            final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
            final Node node = roots.computeIfAbsent(pathKey, k -> new Node(null, 0, root.getName()));
            node.addSpan(root);
            walk(trace, root, node, nodeMutator);
        }
        return roots;
    }

    private void validate(final Collection<Trace> traces,
                          final Map<PathKey, Node> roots) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(Duration.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final NodeMutator nodeValidator = new NodeValidator(spanComparator, pathKeyFactory);
        for (final Trace trace : traces) {
            final Span root = trace.getRoot();
            final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
            final Node node = roots.get(pathKey);
            if (node == null) {
                throw new RuntimeException("Invalid root path: " + pathKey);
            }
            walk(trace, root, node, nodeValidator);
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

    private interface PathKey {

    }

    private interface PathKeyFactory {

        PathKey create(List<Span> spans);
    }

    private static class PathKeyFactoryImpl implements PathKeyFactory {

        @Override
        public PathKey create(final List<Span> spans) {
            if (spans.size() == 1) {
                return new NamePathKey(spans.getFirst().getName());
            }
            final List<String> names = spans.stream().map(Span::getName).toList();
            return new NamesPathKey(names);
        }
    }

    /**
     * A path key that just uses a single name.
     *
     * @param name
     */
    private record NamePathKey(String name) implements PathKey {

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A path key that uses the names of all sub path nodes in the expected order.
     *
     * @param names
     */
    private record NamesPathKey(List<String> names) implements PathKey {

        @Override
        public String toString() {
            return String.join("|", names);
        }
    }

    private interface NodeMutator {

        List<Node> update(List<Span> children, Node node);
    }

    private static class NodeMutatorImpl implements NodeMutator {

        private final Comparator<Span> spanComparator;
        private final PathKeyFactory pathKeyFactory;

        public NodeMutatorImpl(final Comparator<Span> spanComparator,
                               final PathKeyFactory pathKeyFactory) {
            this.spanComparator = spanComparator;
            this.pathKeyFactory = pathKeyFactory;
        }

        @Override
        public List<Node> update(final List<Span> children,
                                 final Node node) {
            final List<Span> sorted = new ArrayList<>(children);
            sorted.sort(spanComparator);
            final PathKey pathKey = pathKeyFactory.create(sorted);
            final List<Node> childNodes = node.targets.computeIfAbsent(pathKey, k -> {
                final List<Node> list = new ArrayList<>();
                children.forEach(s -> list.add(new Node(node, node.depth + 1, s.getName())));
                return list;
            });


            for (int i = 0; i < childNodes.size(); i++) {
                // Add additional span info if wanted.
                final Node target = childNodes.get(i);
                final Span span = children.get(i);
                target.addSpan(span);

                // TODO : Expand min/max/average execution times.
            }

            return childNodes;
        }
    }

    private static class NodeValidator implements NodeMutator {

        private final Comparator<Span> spanComparator;
        private final PathKeyFactory pathKeyFactory;

        public NodeValidator(final Comparator<Span> spanComparator,
                             final PathKeyFactory pathKeyFactory) {
            this.spanComparator = spanComparator;
            this.pathKeyFactory = pathKeyFactory;
        }

        @Override
        public List<Node> update(final List<Span> children,
                                 final Node node) {
            final List<Span> sorted = new ArrayList<>(children);
            sorted.sort(spanComparator);
            final PathKey pathKey = pathKeyFactory.create(sorted);
            final List<Node> childNodes = node.targets.get(pathKey);

            if (childNodes == null) {
                throw new RuntimeException("Invalid path: " + node + " " + pathKey);
            }
            return childNodes;
        }
    }

    private void walk(final Trace trace,
                      final Span parentSpan,
                      final Node parentNode,
                      final NodeMutator nodeMutator) {
        final List<Span> children = trace.getChildren(parentSpan);
        if (!children.isEmpty()) {
            final List<Node> targets = nodeMutator.update(children, parentNode);

            for (int i = 0; i < targets.size(); i++) {
                // Add additional span info if wanted.
                final Node target = targets.get(i);
                final Span span = children.get(i);
                target.addSpan(span);

                // Follow the path deeper.
                walk(trace, span, target, nodeMutator);
            }
        }
    }

    private static class Node {

        private final String name;
        private final Node parent;
        private final int depth;
        private final Map<PathKey, List<Node>> targets = new HashMap<>();
        private final List<Span> spans = new ArrayList<>();

        public Node(final Node parent, final int depth, final String name) {
            this.parent = parent;
            this.depth = depth;
            this.name = name;
        }

        public void addSpan(final Span span) {
            spans.add(span);
        }

        public List<Node> getTargets(final PathKey key,
                                     final Function<PathKey, List<Node>> mappingFunction) {
            return targets.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Node node = (Node) o;
            return Objects.equals(name, node.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            // Get node list in reverse order.
            final List<Node> nodes = new ArrayList<>();
            Node parent = this;
            while (parent != null) {
                nodes.add(parent);
                parent = parent.parent;
            }

            final StringBuilder sb = new StringBuilder();
            for (int i = nodes.size() - 1; i >= 0; i--) {
                final Node node = nodes.get(i);
                for (int j = 0; j < node.depth * 3; j++) {
                    sb.append(' ');
                }
                sb.append(name);
                sb.append("\n");
            }
            return sb.toString();
        }
    }

//    private static class EdgeSet {
//        private final List<Edge> edges = new ArrayList<>();
//
//
//
//    }

//    private static class Edge {
//        private final Vertex from;
//        private final Vertex to;
//        private Duration max;
//        private Duration min;
//        private Duration sum;
//        private long count;
//
//        public Edge(final Vertex from, final Vertex to) {
//            this.from = from;
//            this.to = to;
//        }
//
//        public Vertex getTo() {
//            return to;
//        }
//
//        public void addDuration(final Duration duration) {
//            if (max == null ||
//                max.getSeconds() < duration.getSeconds() ||
//                (max.getSeconds() == duration.getSeconds() && max.getNano() < duration.getNano())) {
//                max = duration;
//            }
//            if (min == null ||
//                min.getSeconds() > duration.getSeconds() ||
//                (min.getSeconds() == duration.getSeconds() && min.getNano() > duration.getNano())) {
//                min = duration;
//            }
//            if (sum == null) {
//                sum = duration;
//            } else {
//                sum = sum.plus(duration);
//            }
//            count++;
//        }
//
//        @Override
//        public boolean equals(final Object o) {
//            if (this == o) {
//                return true;
//            }
//            if (o == null || getClass() != o.getClass()) {
//                return false;
//            }
//            final Edge edge = (Edge) o;
//            return Objects.equals(from, edge.from) &&
//                   Objects.equals(to, edge.to);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(from, to);
//        }
//
//        @Override
//        public String toString() {
//            return "Edge{" +
//                   "from=" + from +
//                   ", to=" + to +
//                   ", max=" + max +
//                   ", min=" + min +
//                   ", sum=" + sum +
//                   ", count=" + count +
//                   '}';
//        }
//    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }

    public static class CloseSpanComparator implements Comparator<Span> {

        private final Duration tolerance;

        public CloseSpanComparator(final Duration tolerance) {
            this.tolerance = tolerance;
        }

        @Override
        public int compare(final Span o1, final Span o2) {
            final Instant start1 = NanoTimeUtil.get(o1.getStartTimeUnixNano());
            final Instant start2 = NanoTimeUtil.get(o2.getStartTimeUnixNano());
            if (start1.isBefore(start2)) {
                // If there is less duration than the supplied tolerance between then sort by name.
                if (Duration.between(start1, start2).compareTo(tolerance) <= 0) {
                    return o1.getName().compareTo(o2.getName());
                }
            } else {
                if (Duration.between(start2, start1).compareTo(tolerance) <= 0) {
                    return o1.getName().compareTo(o2.getName());
                }
            }
            return start1.compareTo(start2);
        }
    }
}
