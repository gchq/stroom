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

public class NodeMutatorImpl implements NodeMutator {

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public NodeMutatorImpl(final Comparator<Span> spanComparator,
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

        final PathNodeList pathNodeList = innerMap.computeIfAbsent(pathKey, k -> {
            final List<PathNode> list = new ArrayList<>();

            children.forEach(s -> {
                final List<String> path = new ArrayList<>(node.getPath());
                path.add(s.getName());
                list.add(new PathNode(s.getName(), path));
            });

            final PathNodeList subNodes = new PathNodeList(pathKey, list);
            node.getTargets().add(subNodes);
            return subNodes;
        });

        for (int i = 0; i < pathNodeList.getNodes().size(); i++) {
            // Add additional span info if wanted.
            final PathNode target = pathNodeList.getNodes().get(i);
            final Span span = children.get(i);
            target.addSpan(span);

            // TODO : Expand min/max/average execution times.
        }

        return pathNodeList;
    }
}
