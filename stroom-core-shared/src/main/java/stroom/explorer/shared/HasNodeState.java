package stroom.explorer.shared;

public interface HasNodeState {
    NodeState getNodeState();

    enum NodeState {
        OPEN, CLOSED, LEAF
    }
}
