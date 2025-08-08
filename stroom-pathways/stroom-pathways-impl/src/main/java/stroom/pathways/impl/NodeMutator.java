package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;

import java.util.List;
import java.util.Map;

public interface NodeMutator {

    PathNodeList update(List<Span> children,
                        PathNode node,
                        Map<String, Map<PathKey, PathNodeList>> map);
}
