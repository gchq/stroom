package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeValidator implements NodeMutator {

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public NodeValidator(final Comparator<Span> spanComparator,
                         final PathKeyFactory pathKeyFactory) {
        this.spanComparator = spanComparator;
        this.pathKeyFactory = pathKeyFactory;
    }

    @Override
    public PathNodeList update(final List<Span> children,
                               final PathNode node,
                               final Map<String, Map<PathKey, PathNodeList>> map) {
        final List<Span> sorted = new ArrayList<>(children);
        sorted.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sorted);

        // Load inner map.
        final Map<PathKey, PathNodeList> innerMap = map.computeIfAbsent(node.getUuid(), k -> {
            final Map<PathKey, PathNodeList> subMap = new HashMap<>();
            node.getTargets().forEach(target -> subMap.put(target.getPathKey(), target));
            return subMap;
        });

        final PathNodeList childNodes = innerMap.get(pathKey);

        if (childNodes == null) {
            throw new RuntimeException("Invalid path: " + node + " " + pathKey);
        }
        return childNodes;
    }
}
