package stroom.analytics.impl;

import java.util.Objects;

/**
 * Holds the state of whether execution is enabled on the node or not.
 */
public record ExecutionNode(String nodeName, boolean isEnabled) {

    public ExecutionNode {
        Objects.requireNonNull(nodeName);
    }
}
