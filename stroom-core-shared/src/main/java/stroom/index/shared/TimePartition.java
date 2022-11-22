package stroom.index.shared;

import stroom.index.shared.IndexDoc.PartitionBy;

import java.util.Objects;

public class TimePartition implements Partition {

    private final PartitionBy partitionBy;
    private final int partitionSize;
    private final long partitionFromTime;
    private final long partitionToTime;
    private final String label;

    public TimePartition(final PartitionBy partitionBy,
                         final int partitionSize,
                         final long partitionFromTime,
                         final long partitionToTime,
                         final String label) {
        this.partitionBy = partitionBy;
        this.partitionSize = partitionSize;
        this.partitionFromTime = partitionFromTime;
        this.partitionToTime = partitionToTime;
        this.label = label;
    }

    public PartitionBy getPartitionBy() {
        return partitionBy;
    }

    public int getPartitionSize() {
        return partitionSize;
    }

    @Override
    public Long getPartitionFromTime() {
        return partitionFromTime;
    }

    @Override
    public Long getPartitionToTime() {
        return partitionToTime;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimePartition that = (TimePartition) o;
        return partitionFromTime == that.partitionFromTime && partitionToTime == that.partitionToTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionFromTime, partitionToTime);
    }

    @Override
    public String toString() {
        return "TimePartition{" +
                "partitionBy=" + partitionBy +
                ", partitionSize=" + partitionSize +
                ", partitionFromTime=" + partitionFromTime +
                ", partitionToTime=" + partitionToTime +
                ", label='" + label + '\'' +
                '}';
    }
}
