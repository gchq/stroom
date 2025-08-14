package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class PathNodeList {

    @JsonProperty
    private final PathKey pathKey;
    @JsonProperty
    private final List<PathNode> nodes;

    @JsonCreator
    public PathNodeList(@JsonProperty("pathKey") final PathKey pathKey,
                        @JsonProperty("nodes") final List<PathNode> nodes) {
        this.pathKey = pathKey;
        this.nodes = nodes;
    }

    public PathKey getPathKey() {
        return pathKey;
    }

    public List<PathNode> getNodes() {
        return nodes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathNodeList that = (PathNodeList) o;
        return Objects.equals(pathKey, that.pathKey) &&
               Objects.equals(nodes, that.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathKey, nodes);
    }

    @Override
    public String toString() {
        return "PathNodeList{" +
               "pathKey=" + pathKey +
               ", nodes=" + nodes +
               '}';
    }
}
