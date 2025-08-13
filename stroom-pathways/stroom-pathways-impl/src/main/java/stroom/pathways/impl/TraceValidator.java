package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TraceValidator implements TraceProcessor {

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public TraceValidator(final Comparator<Span> spanComparator,
                          final PathKeyFactory pathKeyFactory) {
        this.spanComparator = spanComparator;
        this.pathKeyFactory = pathKeyFactory;
    }

    @Override
    public void process(final Trace trace, final Map<PathKey, PathNode> roots) {
        final Map<PathKey, Map<String, Map<PathKey, PathNodeList>>> maps = new HashMap<>();
        final Span root = trace.getRoot();
        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
        final PathNode node = roots.computeIfAbsent(pathKey, k -> new PathNode(root.getName()));
        final Map<String, Map<PathKey, PathNodeList>> map = maps.computeIfAbsent(pathKey, k -> new HashMap<>());
        walk(trace, root, node, map);
    }

    private void walk(final Trace trace,
                      final Span parentSpan,
                      final PathNode parentNode,
                      final Map<String, Map<PathKey, PathNodeList>> map) {
        final List<Span> childSpans = trace.getChildren(parentSpan);
        final List<Span> sortedSpans = new ArrayList<>(childSpans);
        sortedSpans.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sortedSpans);

        // Load inner map.
        final Map<PathKey, PathNodeList> innerMap = map.computeIfAbsent(parentNode.getUuid(), k ->
                parentNode
                        .getTargets()
                        .stream()
                        .collect(Collectors.toMap(PathNodeList::getPathKey, Function.identity())));

        final PathNodeList childNodes = innerMap.get(pathKey);
        if (childNodes == null) {
            throw new RuntimeException("Invalid path: " + parentNode + " " + pathKey);
        }

        // Follow the path deeper.
        for (int i = 0; i < childNodes.getNodes().size(); i++) {
            final PathNode target = childNodes.getNodes().get(i);
            final Span span = sortedSpans.get(i);
            walk(trace, span, target, map);
        }
    }
}
