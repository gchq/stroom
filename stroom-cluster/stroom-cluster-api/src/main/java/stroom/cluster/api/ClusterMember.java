package stroom.cluster.api;

public class ClusterMember {
    private final String uuid;

    public ClusterMember(final String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }
}
