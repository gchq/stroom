package stroom.pathways.impl;

import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;

import java.util.Map;

public interface TraceWalker {

    void process(Trace trace,
                 Map<PathKey, PathNode> roots,
                 MessageReceiver messageReceiver,
                 PathwaysDoc pathwaysDoc);
}
