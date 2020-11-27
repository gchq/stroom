package stroom.data.client;

import stroom.pipeline.shared.SourceLocation;

import java.util.Objects;
import java.util.Optional;

public class SourceKey {
    private final long metaId;
    private final long partNo;
    private final Long segmentNo;
    private final String childStreamType;

    public SourceKey(final long metaId,
                     final long partNo,
                     final Long segmentNo,
                     final String childStreamType) {
        this.metaId = metaId;
        this.partNo = partNo;
        this.segmentNo = segmentNo;
        this.childStreamType = childStreamType;
    }

    public SourceKey(SourceLocation sourceLocation) {
        this.metaId = sourceLocation.getId();
        this.partNo = sourceLocation.getPartNo();
        this.segmentNo = sourceLocation.getSegmentNo();
        this.childStreamType = sourceLocation.getChildType();
    }

    public long getMetaId() {
        return metaId;
    }

    public Optional<String> getOptChildStreamType() {
        return Optional.ofNullable(childStreamType);
    }

    /**
     * @return The part number, zero based. 0 for single-part segmented streams.
     */
    public long getPartNo() {
        return partNo;
    }

    public Optional<Long> getSegmentNo() {
        return Optional.ofNullable(segmentNo);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SourceKey sourceKey = (SourceKey) o;
        return metaId == sourceKey.metaId &&
                partNo == sourceKey.partNo &&
                Objects.equals(segmentNo, sourceKey.segmentNo) &&
                Objects.equals(childStreamType, sourceKey.childStreamType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, partNo, segmentNo, childStreamType);
    }

    @Override
    public String toString() {
        return metaId + ":"
                + partNo + ":"
                + getSegmentNo().orElse(0L) + " - "
                + childStreamType;
    }
}

