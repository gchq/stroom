package stroom.cluster.api;

public class ClusterRole {
    private final String name;

    public ClusterRole(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
