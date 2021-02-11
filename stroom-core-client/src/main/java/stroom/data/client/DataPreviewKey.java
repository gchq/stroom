package stroom.data.client;

import stroom.pipeline.shared.SourceLocation;

import java.util.Objects;

public class DataPreviewKey {

    private final long metaId;

    public DataPreviewKey(final long metaId) {
        this.metaId = metaId;
    }

    public DataPreviewKey(SourceLocation sourceLocation) {
        this.metaId = sourceLocation.getId();
    }

    public long getMetaId() {
        return metaId;
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataPreviewKey that = (DataPreviewKey) o;
        return metaId == that.metaId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId);
    }

    @Override
    public String toString() {
        return "DataPreviewKey{" +
                "metaId=" + metaId +
                '}';
    }
}

