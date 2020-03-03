package stroom.index.impl;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

@JsonInclude(Include.NON_DEFAULT)
public class CreateVolumeDTO {
    @Nullable
    @JsonProperty
    private String nodeName;
    @Nullable
    @JsonProperty
    private String path;
    @JsonProperty
    private String indexVolumeGroupName;

    public CreateVolumeDTO() {
    }

    @JsonCreator
    public CreateVolumeDTO(@JsonProperty("nodeName") @Nullable final String nodeName,
                           @JsonProperty("path") @Nullable final String path,
                           @JsonProperty("indexVolumeGroupName") final String indexVolumeGroupName) {
        this.nodeName = nodeName;
        this.path = path;
        this.indexVolumeGroupName = indexVolumeGroupName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIndexVolumeGroupName() {
        return indexVolumeGroupName;
    }

    public void setIndexVolumeGroupName(String indexVolumeGroupName) {
        this.indexVolumeGroupName = indexVolumeGroupName;
    }
}
