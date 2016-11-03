package stroom.util.shared;

public interface HasNodeState {
    enum NodeState {
        OPEN, CLOSED, LEAF
    }

    NodeState getNodeState();
}
