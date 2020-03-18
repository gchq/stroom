package stroom.index.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CreateVolumeRequest {
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final String indexVolumeGroupName;

    @JsonCreator
    public CreateVolumeRequest(@JsonProperty("nodeName") final String nodeName,
                               @JsonProperty("path") final String path,
                               @JsonProperty("indexVolumeGroupName") final String indexVolumeGroupName) {
        this.nodeName = nodeName;
        this.path = path;
        this.indexVolumeGroupName = indexVolumeGroupName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getPath() {
        return path;
    }

    public String getIndexVolumeGroupName() {
        return indexVolumeGroupName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CreateVolumeRequest that = (CreateVolumeRequest) o;
        return Objects.equals(nodeName, that.nodeName) &&
                Objects.equals(path, that.path) &&
                Objects.equals(indexVolumeGroupName, that.indexVolumeGroupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, path, indexVolumeGroupName);
    }

    @Override
    public String toString() {
        return "CreateVolumeRequest{" +
                "nodeName='" + nodeName + '\'' +
                ", path='" + path + '\'' +
                ", indexVolumeGroupName='" + indexVolumeGroupName + '\'' +
                '}';
    }
}
