package stroom.data.client;

import java.util.Objects;

public class SourceKey {
    private final long metaId;
    private final String childStreamType;

    public SourceKey(final long metaId, final String childStreamType) {
        this.metaId = metaId;
        this.childStreamType = childStreamType;
    }

    public long getMetaId() {
        return metaId;
    }

    public String getChildStreamType() {
        return childStreamType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SourceKey sourceKey = (SourceKey) o;
        return metaId == sourceKey.metaId &&
                Objects.equals(childStreamType, sourceKey.childStreamType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, childStreamType);
    }

    @Override
    public String toString() {
        return metaId + " - " + childStreamType;
    }
}

