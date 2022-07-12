package stroom.cluster.api;

import java.util.Objects;
import java.util.UUID;

public class ClusterMember {

    private final String uuid;

    public ClusterMember(final UUID uuid) {
        this.uuid = uuid.toString();
    }

    public ClusterMember(final String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ClusterMember that = (ClusterMember) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return uuid;
    }
}
