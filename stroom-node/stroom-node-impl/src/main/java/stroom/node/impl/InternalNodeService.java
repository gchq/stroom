package stroom.node.impl;

import stroom.node.api.NodeService;
import stroom.node.shared.Node;

public interface InternalNodeService extends NodeService {
    Node create(String name);
}
