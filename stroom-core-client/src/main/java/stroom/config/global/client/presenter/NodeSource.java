package stroom.config.global.client.presenter;

import java.util.Objects;

public class NodeSource {
    private final String nodeName;
    private final String source;

    public NodeSource(final String nodeName,
                      final String source) {
        this.nodeName = Objects.requireNonNull(nodeName);
        this.source = Objects.requireNonNull(source);
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final NodeSource that = (NodeSource) o;
        return nodeName.equals(that.nodeName) &&
            source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, source);
    }

    @Override
    public String toString() {
        return "NodeSource{" +
            "nodeName='" + nodeName + '\'' +
            ", source='" + source + '\'' +
            '}';
    }
}
