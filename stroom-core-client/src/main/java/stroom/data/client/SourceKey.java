package stroom.data.client;

import java.util.Objects;

public class SourceKey {
    private final long metaId;
    private final String streamType;

    public SourceKey(final long metaId, final String streamType) {
        this.metaId = metaId;
        this.streamType = streamType;
    }

    public long getMetaId() {
        return metaId;
    }

    public String getStreamType() {
        return streamType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SourceKey sourceKey = (SourceKey) o;
        return metaId == sourceKey.metaId &&
                Objects.equals(streamType, sourceKey.streamType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, streamType);
    }

    @Override
    public String toString() {
        return metaId + " - " + streamType;
    }
}

