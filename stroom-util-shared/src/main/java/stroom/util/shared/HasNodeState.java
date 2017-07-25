package stroom.util.shared;

public interface HasNodeState {
    NodeState getNodeState();

    enum NodeState {
        OPEN, CLOSED, LEAF
    }
}
