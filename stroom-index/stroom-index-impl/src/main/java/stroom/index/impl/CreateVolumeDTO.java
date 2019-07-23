package stroom.index.impl;

public class CreateVolumeDTO {
    private String nodeName;
    private String path;
    private int indexVolumeGroupId;

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

    public int getIndexVolumeGroupId() {
        return indexVolumeGroupId;
    }

    public void setIndexVolumeGroupId(int indexVolumeGroupId) {
        this.indexVolumeGroupId = indexVolumeGroupId;
    }
}
