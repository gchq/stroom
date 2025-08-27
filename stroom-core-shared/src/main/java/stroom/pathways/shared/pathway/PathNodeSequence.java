package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class PathNodeSequence {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final PathKey pathKey;
    @JsonProperty
    private final List<PathNode> nodes;

    @JsonCreator
    public PathNodeSequence(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("pathKey") final PathKey pathKey,
                            @JsonProperty("nodes") final List<PathNode> nodes) {
        this.uuid = uuid;
        this.pathKey = pathKey;
        this.nodes = nodes;
    }

    public String getUuid() {
        return uuid;
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
        final PathNodeSequence that = (PathNodeSequence) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "PathNodeList{" +
               "uuid='" + uuid + '\'' +
               ", pathKey=" + pathKey +
               ", nodes=" + nodes +
               '}';
    }
}
