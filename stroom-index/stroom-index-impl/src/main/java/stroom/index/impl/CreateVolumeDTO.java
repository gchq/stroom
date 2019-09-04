package stroom.index.impl;


import javax.annotation.Nullable;

public class CreateVolumeDTO {
    @Nullable
    private String nodeName;
    @Nullable
    private String path;
    private String indexVolumeGroupName;

    public CreateVolumeDTO() {

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
